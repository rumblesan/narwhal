
Narwhal {

  var logger;
  var synths;
  var globalFX;
  var globalBus;

  var tonic;
  var defaultOctave;
  var scale;
  var synthParamMap, synthScaleFuncs,
      synthFXParamMap, synthFXScaleFuncs,
      globalFXParamMap, globalFXScaleFuncs;
  var shaperBuffer;

  init { | s, debug = false |
    logger = NarwhalLogger.new.init(debug);
    this.defineSynths(s);
    tonic = 24;
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
      var param = msg[1];
      var value = msg[2];
      if (param.notNil && value.notNil, {
        this.setGlobalFXParam(param, value);
      }, {
        logger.error("Invalid global fx message arguments");
      });
    }, '/g', NetAddr("localhost"), oscPort);

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
    globalFXParamMap = Dictionary.new;
    globalFXScaleFuncs = Dictionary.new;

    this.addSynthParam(0, \wave, {|v| v.clip(0, 1);});
    this.addSynthParam(1, \cutoff, {|v| ((v/35) * 3000) + 20;});
    this.addSynthParam(2, \resonance, {|v| ((v/35) * 3) + 0.1;});
    this.addSynthParam(3, \sustain, {|v| ((v/35) * 3) + 0.1;});
    this.addSynthParam(4, \decay, {|v| ((v/35) * 3) + 0.1;});
    this.addSynthParam(5, \envelope, {|v| (v * 60);});
    this.addSynthParam(6, \volume, {|v| (v/35) * 1.1;});
    this.addSynthParam(7, \distortion, {|v| (v/35);});
    this.addSynthParam(8, \gain, {|v| (v/5) + 0.9;});

    this.addSynthFXParam(0, \delayTime, {|v| (v/35);});
    this.addSynthFXParam(1, \delayFeedback, {|v| (v/35);});
    this.addSynthFXParam(2, \reverbMix, {|v| (v/35);});
    this.addSynthFXParam(3, \reverbRoom, {|v| (v/35);});
    this.addSynthFXParam(4, \reverbDamping, {|v| (v/35);});

    this.addGlobalFXParam(0, \reverbMix, {|v| (v/35);});
    this.addGlobalFXParam(1, \reverbRoom, {|v| (v/35);});
    this.addGlobalFXParam(2, \reverbDamping, {|v| (v/35);});
  }

  addSynthParam { | n, name,  func |
    synthParamMap.put(n, name);
    synthScaleFuncs.put(name, func);
  }

  addSynthFXParam { | n, name,  func |
    synthFXParamMap.put(n, name);
    synthFXScaleFuncs.put(name, func);
  }

  addGlobalFXParam { | n, name,  func |
    globalFXParamMap.put(n, name);
    globalFXScaleFuncs.put(name, func);
  }

  playSynth { | n, note, octave |
    this.actionVoice(n, \synth, { | synth |
      var freq = scale.degreeToFreq(note, tonic.midicps, octave);
      logger.debug("Playing synth %: %hz".format(n, freq));
      synth.set(\freq, freq);
      synth.set(\t_trig, 1);
    });
  }

  playDrum { | n |
    switch(n,
      0, { Synth(\narwhalKick, [\out, globalBus]) },
      1, { Synth(\narwhalSnare, [\out, globalBus]) },
      2, { Synth(\narwhalHat, [\out, globalBus]) }
    );
  }

  setSynthParam { | voice, param, value |
    this.actionVoice(voice, \synth, { | synth |
      var paramName = synthParamMap[param];
      var scaledValue = synthScaleFuncs[paramName].value(value);
      logger.debug("Setting synth % % to % -> %".format(voice, paramName, value, scaledValue));
      synth.set(paramName, scaledValue);
    });
  }

  setSynthFXParam { | voice, param, value |
    this.actionVoice(voice, \fx, { | synthFX |
      var paramName = synthFXParamMap[param];
      var scaledValue = synthFXScaleFuncs[paramName].value(value);
      logger.debug("Setting synth % fx % to % -> %".format(voice, paramName, value, scaledValue));
      synthFX.set(paramName, scaledValue);
    });
  }

  setGlobalFXParam { | param, value |
    var paramName = globalFXParamMap[param];
    var scaledValue = globalFXScaleFuncs[paramName].value(value);
    logger.debug("Setting global fx % to % -> %".format(paramName, value, scaledValue));
    globalFX.set(paramName, scaledValue);
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

    SynthDef(\narwhalGlobalFX, {
      arg in, out, reverbMix=0.33, reverbRoom=0.5, reverbDamping=0.5;
      var inputSignal = In.ar(in, 2);
      var reverbed = FreeVerb2.ar(inputSignal[0], inputSignal[1], reverbMix, reverbRoom, reverbDamping);
      Out.ar(out, reverbed);
    }).add;

    SynthDef(\narwhalSynth, {
      arg out, freq=440, wave=0, cutoff=100, resonance=0.2,
          sustain=0, decay=1.0, envelope=1000, t_trig=0, volume=0.2, gain=1.3, distortion=0.1;
      var filEnv, volEnv, waves, voice, shaped, distorted, compressed;

      volEnv = EnvGen.ar(
        Env.new([10e-10, 1, 1, 10e-10], [0.01, sustain, decay], 'exp'),
        t_trig);
      filEnv = EnvGen.ar(
        Env.new([10e-10, 1, 10e-10], [0.01, decay], 'exp'),
        t_trig);
      waves = [Saw.ar(freq, volEnv), Pulse.ar(freq, 0.5, volEnv)];
      voice = RLPF.ar(
        Select.ar(wave, waves),
        cutoff + (filEnv * envelope),
        resonance
      ).dup;
      shaped = Shaper.ar(shaperBuffer, voice, 0.5);
      distorted = (((shaped * distortion) + (voice * (1 - distortion))) * gain).distort;

      compressed = Compander.ar(distorted, distorted, 0.3);

      Out.ar(out, compressed * volume);
    }).add;

    SynthDef(\narwhalSynthFX, {
      arg in, out, delayTime=0.25, delayFeedback=0.2, reverbMix=0, reverbRoom=0.5, reverbDamping=0.5;
      var inputSignal, delayed, reverbed;
      inputSignal = In.ar(in, 2);

      delayed = inputSignal + (LocalIn.ar(2) * delayFeedback);

      reverbed = FreeVerb2.ar(delayed[0], delayed[1], reverbMix, reverbRoom, reverbDamping);

      LocalOut.ar(DelayL.ar(delayed, 2, delayTime));

      Out.ar(out, reverbed);
    }, [nil, nil, 0.5, 0.1, 0.1, 0.1, 0.1]).add;

  }

  setupAudio { | s, voiceCount |
    logger.log("Setting up audio");

    globalBus = Bus.audio(s, 2);
    globalFX = Synth(\narwhalGlobalFX, [\in, globalBus, \out, 0]);

    synths = voiceCount.collect { | c |
      var voice, synth, fx, voiceBus;
      voiceBus = Bus.audio(s, 2);
      fx = Synth(\narwhalSynthFX, [\in, voiceBus, \out, globalBus]);
      synth = Synth(\narwhalSynth, [\out, voiceBus]);
      voice = Dictionary.new;
      voice.put(\synth, synth);
      voice.put(\bus, voiceBus);
      voice.put(\fx, fx);
      voice
    };
  }

}

