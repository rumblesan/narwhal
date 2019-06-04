
Narwhal {

  var logger;
  var synths;
  var synthFX;
  var drumFX;
  var globalFX;

  var tonic;
  var defaultOctave;
  var scale;
  var synthParamMap, synthScaleFuncs;
  var synthFXParamMap, synthFXScaleFuncs;

  init { | s, debug = false |
    logger = NarwhalLogger.new.init(debug);
    this.defineSynths;
    tonic = 36;
    defaultOctave = 2;
    scale = Scale.minor;
  }

  setup { | oscInPort, voices = 4 |
    this.setupAudio(voices);
    this.setupParamMaps();
    this.setupOSC(oscInPort);
  }

  actionSynth{| synthNumber, action |
    if (synths[synthNumber].notNil, {
      action.value(synths[synthNumber]);
    }, {
      logger.error("No synth numbered %".format(synthNumber));
    });
  }

  setupOSC { | oscPort |
    logger.log("Setting up OSC listeners");

    OSCFunc({ | msg |
      var voice = msg[1];
      var note = msg[2];
      var octave = msg[3];
      if (voice.notNil && note.notNil, {
        if (octave.isNil, { octave = defaultOctave });
        this.playSynth(voice, note, octave);
      }, {
        logger.error("Invalid note message arguments: % - %".format(voice, note));
      })
    }, '/n', NetAddr("localhost"), oscPort);

    OSCFunc({ | msg |
      var voice = msg[1];
      var param = msg[2];
      var value = msg[3];
      if (param.notNil && value.notNil, {
        this.setSynthParam(voice, param, value);
      }, {
        logger.error("Invalid parameter message arguments");
      });
    }, '/p', NetAddr("localhost"), oscPort);

    OSCFunc({ | msg |
      var param = msg[1];
      var value = msg[2];
      if (param.notNil && value.notNil, {
        this.setSynthFXParam(param, value);
      }, {
        logger.error("Invalid fx message arguments");
      });
    }, '/f', NetAddr("localhost"), oscPort);

    OSCFunc({ | msg |
      var instrument = msg[1];
      if (instrument.notNil, {
        this.playDrum(instrument);
      }, {
        logger.error("Invalid fx drum instrument");
      });
    }, '/d', NetAddr("localhost"), oscPort);

  }

  setupParamMaps {
    synthParamMap = Dictionary.new;
    synthScaleFuncs = Dictionary.new;
    synthFXParamMap = Dictionary.new;
    synthFXScaleFuncs = Dictionary.new;

    this.addSynthParam(0, \wave, { arg v; v.clip(0, 1);});
    this.addSynthParam(1, \cutoff, { arg v; ((v/35) * 3000) + 20;});
    this.addSynthParam(2, \resonance, { arg v; ((v/35) * 3) + 0.1;});
    this.addSynthParam(3, \sustain, { arg v; ((v/35) * 3) + 0.1;});
    this.addSynthParam(4, \decay, { arg v; ((v/35) * 3) + 0.1;});
    this.addSynthParam(5, \envelope, { arg v; (v/35) + 0.1;});
    this.addSynthParam(6, \volume, { arg v; (v/35) * 1.1;});

    this.addSynthFXParam(0, \delay, { arg v; (v/35);});
  }

  addSynthParam { | n, name,  func |
    synthParamMap.put(n, name);
    synthScaleFuncs.put(name, func);
  }

  addSynthFXParam { | n, name,  func |
    synthFXParamMap.put(n, name);
    synthFXScaleFuncs.put(name, func);
  }

  playSynth { | n, note, octave |
    this.actionSynth(n, { | synth |
      var freq = scale.degreeToFreq(note, tonic.midicps, octave);
      logger.debug("Playing synth %: %hz".format(n, freq));
      Routine {
        synth.set(\freq, freq);
        synth.set(\gate, 1);
        0.01.wait;
        synth.set(\gate, 0);
      }.play;
    });
  }

  playDrum { | n |
    switch(n,
      0, { Synth(\narwhalKick, [\out, [6, 7]]) },
      1, { Synth(\narwhalSnare, [\out, [6, 7]]) },
      2, { Synth(\narwhalHat, [\out, [6, 7]]) }
    );
  }

  setSynthParam { | n, param, value |
    this.actionSynth(n, { | synth |
      var paramName = synthParamMap[param];
      var scaledValue = synthScaleFuncs[paramName].value(value);
      logger.debug("Setting synth % % to %".format(n, paramName, scaledValue));
      synth.set(paramName, scaledValue);
    });
  }

  setSynthFXParam { | param, value |
    var paramName = synthFXParamMap[param];
    var scaledValue = synthFXScaleFuncs[paramName].value(value);
    logger.debug("Setting fx % to %".format(paramName, scaledValue));
    synthFX.set(paramName, scaledValue);
  }

  defineSynths {
    logger.log("Defining synths");

    SynthDef(\narwhalKick, { arg out, amp=0.5;
      var amp_env, phase_env, phase, freq, dur;

      freq = 10.rand + 90;
      dur = 0.25;

      amp_env   = EnvGen.ar(Env.perc(1e-6,dur), doneAction:2);
      phase_env = EnvGen.ar(Env.perc(1e-6,0.125));

      phase = SinOsc.ar(20,0,pi) * phase_env;
      Out.ar(out, SinOsc.ar([freq,1.01*freq],phase) * amp_env * amp);
    }).add;

    SynthDef(\narwhalSnare, { arg out, amp=0.5;
      var amp_env, cut_freq, dur;

      cut_freq = 3000;
      dur = [0.0625, 0.125, 0.25].choose;

      amp_env = EnvGen.ar(Env.perc(1e-6, dur), doneAction:2);
      Out.ar(out, LPF.ar( {WhiteNoise.ar(WhiteNoise.ar)}.dup * amp_env, cut_freq ) * amp);
    }).add;

    SynthDef(\narwhalHat, { arg out, amp=0.5;
      var amp_env, cut_freq, dur;

      cut_freq = 6000;
      dur = [0.0625, 0.125, 0.25].choose;

      amp_env = EnvGen.ar(Env.perc(1e-7, dur), doneAction:2);
      Out.ar(out, HPF.ar( {WhiteNoise.ar}.dup * amp_env, cut_freq ) * amp / 4);
    }).add;

    SynthDef(\narwhalSynthFX, {
      arg in, out=0, delay=0.25;
      var signal = In.ar(in);

      var delayChain = DelayN.ar(signal, 2, delay, 1, mul: 0.3);
      var reverb = GVerb.ar(delayChain, 100, 1, mul: 0.3);

      Out.ar(out, reverb + delayChain + signal);
    }).add;

    SynthDef(\narwhalDrumFX, {
      arg in, out=0, delay=0.25;
      var signal = In.ar(in);

      var reverb = GVerb.ar(signal, 100, 1, mul: 0.3);

      Out.ar(out, reverb + signal);
    }).add;

    // copied from https://sccode.org/1-4Wy
    SynthDef(\narwhalSynth, {
      arg out=0, freq=440, wave=0, cutoff=100, resonance=0.2,
          sustain=0, decay=1.0, envelope=1000, gate=0, volume=0.2;
      var filEnv, volEnv, waves;

      volEnv = EnvGen.ar(
        Env.new([10e-10, 1, 1, 10e-10], [0.01, sustain, decay], 'exp'),
        gate);
      filEnv = EnvGen.ar(
        Env.new([10e-10, 1, 10e-10], [0.01, decay], 'exp'),
        gate);
      waves = [Saw.ar(freq, volEnv), Pulse.ar(freq, 0.5, volEnv)];

      Out.ar(out, RLPF.ar(
        Select.ar(wave, waves),
        cutoff + (filEnv * envelope),
        resonance
      ).dup * volume);
    }).add;

  }

  setupAudio { | voiceCount |
    logger.log("Setting up audio");

    synthFX = Synth(\narwhalSynthFX, [\in, 2, \out, [0, 1]]);
    drumFX = Synth(\narwhalDrumFX, [\in, 3, \out, [0, 1]]);

    synths = voiceCount.collect { | c |
      Synth(\narwhalSynth, [\out, 2]);
    };
  }

}

