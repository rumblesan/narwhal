
Narwhal {

  var logger;
  var synths;
  var synthFX;
  var drumFX;
  var globalFX;
  var synthBus;

  var tonic;
  var defaultOctave;
  var scale;
  var synthParamMap, synthScaleFuncs;
  var synthFXParamMap, synthFXScaleFuncs;
  var shaperBuffer;

  init { | s, debug = false |
    logger = NarwhalLogger.new.init(debug);
    this.defineSynths(s);
    tonic = 36;
    defaultOctave = 2;
    scale = Scale.minor;
  }

  setup { | s, oscInPort, voices = 4 |
    this.setupAudio(s, voices);
    this.setupParamMaps();
    this.setupOSC(oscInPort);
  }

  actionVoice{| voiceNumber, section, action |
    if (synths[voiceNumber].notNil, {
      action.value(synths[voiceNumber][section]);
    }, {
      logger.error("No voice numbered %".format(voiceNumber));
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
      var voice = msg[1];
      var param = msg[2];
      var value = msg[3];
      if (param.notNil && value.notNil, {
        this.setSynthFXParam(voice, param, value);
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
    this.addSynthParam(5, \envelope, { arg v; (v * 60);});
    this.addSynthParam(6, \volume, { arg v; (v/35) * 1.1;});
    this.addSynthParam(7, \distortion, { arg v; (v/35);});

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
    this.actionVoice(n, \synth, { | synth |
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

  setSynthParam { | voice, param, value |
    this.actionVoice(voice, \synth, { | synth |
      var paramName = synthParamMap[param];
      var scaledValue = synthScaleFuncs[paramName].value(value);
      logger.debug("Setting synth % % to %".format(voice, paramName, scaledValue));
      synth.set(paramName, scaledValue);
    });
  }

  setSynthFXParam { | voice, param, value |
    this.actionVoice(voice, \fx, { | synthFX |
      var paramName = synthFXParamMap[param];
      var scaledValue = synthFXScaleFuncs[paramName].value(value);
      logger.debug("Setting fx % % to %".format(voice, paramName, scaledValue));
      synthFX.set(paramName, scaledValue);
    });
  }

  defineSynths { | s |
    logger.log("Defining synths");

    //shaperBuffer = Buffer.alloc(s, 512, 1, { |buf| buf.chebyMsg([1,0,1,1,0,1])});
    shaperBuffer = Buffer.alloc(s, 512, 1, {arg buf; buf.chebyMsg([0.25,0.5,0.25], false)});

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
      arg in, out, delay=0.25;
      var mix=0.33, room=0.5, damp=0.5;
      var signal = In.ar(in, 2);

      var delayChain = DelayN.ar(signal, 2, delay, 1, mul: 0.3);
      var reverb = FreeVerb2.ar(delayChain[0], delayChain[1], mix, room, damp);

      Out.ar(out, reverb + delayChain + signal);
    }).add;

    SynthDef(\narwhalGlobalFX, {
      arg in, out, mix=0.33, room=0.5, damp=0.5;
      var signal = In.ar(in, 2);
      var reverb = FreeVerb2.ar(signal[0], signal[1], mix, room, damp);
      Out.ar(out, reverb);
    }).add;

    SynthDef(\narwhalSynth, {
      arg out, freq=440, wave=0, cutoff=100, resonance=0.2,
          sustain=0, decay=1.0, envelope=1000, gate=0, volume=0.2, distortion=0.1;
      var filEnv, volEnv, waves, voice, shaped;

      volEnv = EnvGen.ar(
        Env.new([10e-10, 1, 1, 10e-10], [0.01, sustain, decay], 'exp'),
        gate);
      filEnv = EnvGen.ar(
        Env.new([10e-10, 1, 10e-10], [0.01, decay], 'exp'),
        gate);
      waves = [Saw.ar(freq, volEnv), Pulse.ar(freq, 0.5, volEnv)];
      voice = RLPF.ar(
        Select.ar(wave, waves),
        cutoff + (filEnv * envelope),
        resonance
      ).dup;
      shaped = Shaper.ar(shaperBuffer, voice, 0.5);

      Out.ar(out, ((shaped * distortion) + (voice * (1 - distortion))) * volume);
    }).add;

  }

  setupAudio { | s, voiceCount |
    logger.log("Setting up audio");

    synthBus = Bus.audio(s, 2);
    globalFX = Synth(\narwhalGlobalFX, [\in, synthBus, \out, 0]);

    synths = voiceCount.collect { | c |
      var voice, synth, fx, voiceBus;
      voiceBus = Bus.audio(s, 2);
      fx = Synth(\narwhalSynthFX, [\in, voiceBus, \out, synthBus]);
      synth = Synth(\narwhalSynth, [\out, voiceBus]);
      voice = Dictionary.new;
      voice.put(\synth, synth);
      voice.put(\bus, voiceBus);
      voice.put(\fx, fx);
      voice
    };
  }

}

