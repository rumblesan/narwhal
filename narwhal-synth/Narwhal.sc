
Narwhal {

  var logger;

  var tonic;
  var defaultOctave;
  var scale;

  var synth303Voices;
  var drum808Sounds;
  var globalFX;
  var globalBus;

  var synth303ParamMap, synth303ScaleFuncs,
      synth303FXParamMap, synth303FXScaleFuncs,
      drum808ParamMap, drum808SoundParamMap, drum808ScaleFuncs,
      drum808FXParamMap, drum808FXScaleFuncs,
      globalFXParamMap, globalFXScaleFuncs;
  var shaperBuffer;

  init { | s, debug = false |
    logger = NarwhalLogger.new.init(debug);
    this.defineSynths(s);
    tonic = 24;
    defaultOctave = 2;
    scale = Scale.minor;
  }

  setup { | s, voices = 4 |
    this.setupAudio(s, voices);
    this.setupParamMaps();
  }

  action303Voice{| voiceNumber, section, action |
    if (synth303Voices[voiceNumber].notNil, {
      action.value(synth303Voices[voiceNumber][section]);
    }, {
      logger.error("No voice numbered %".format(voiceNumber));
    });
  }

  action808Drum{| instrument, action |
    if (drum808Sounds[instrument].notNil, {
      action.value(drum808Sounds[instrument]);
    }, {
      logger.error("No 808 sound named %".format(instrument));
    });
  }

  setupOrcaOSC { | oscPort |
    logger.log("Setting up OSC listeners for Orca");

    OSCFunc({ | msg |
      var instrument = "%%%".format(msg[1], msg[2], msg[3]);
      var args = msg[4..];
      switch(instrument,
        "303", { this.trigger303(args); },
        "808", { this.trigger808(args); },
        { logger.error("% is not a valid instrument".format(instrument)); }
      );
    }, '/t', NetAddr("localhost"), oscPort);

    OSCFunc({ | msg |
      var instrument = "%%%".format(msg[1], msg[2], msg[3]);
      var args = msg[4..];
      switch(instrument,
        "303", { this.set303Param(args); },
        "808", { this.set808Param(args); },
        { logger.error("% is not a valid instrument".format(instrument)); }
      );
    }, '/p', NetAddr("localhost"), oscPort);

    OSCFunc({ | msg |
      var instrument = "%%%".format(msg[1], msg[2], msg[3]);
      var args = msg[4..];
      switch(instrument,
        "303", { this.set303FXParam(args); },
        "808", { this.set808FXParam(args); },
        { logger.error("% is not a valid instrument".format(instrument)); }
      );
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

  }

  trigger303 { |args|
    var voiceNum = args[0];
    var note = args[1];
    var octave = defaultOctave;
    if (note.isNil, { ^nil; });
    if (args[2].notNil, { octave = args[2] });
    this.action303Voice(voiceNum, \synth, { | synth |
      var freq = scale.degreeToFreq(note, tonic.midicps, octave);
      logger.debug("Triggering 303 %: %hz".format(voiceNum, freq));
      synth.set(\freq, freq);
      synth.set(\t_trig, 1);
    });
  }

  set303Param { |args|
    var voice = args[0];
    var param = args[1];
    var value = args[2];
    if (param.isNil || value.isNil, {
      logger.error("Invalid parameter message arguments");
      ^nil;
    });
    this.action303Voice(voice, \synth, { | synth |
      var paramName = synth303ParamMap[param];
      var scaledValue = synth303ScaleFuncs[paramName].value(value);
      logger.debug("Setting synth % % to % -> %".format(voice, paramName, value, scaledValue));
      synth.set(paramName, scaledValue);
    });
  }

  set303FXParam { |args|
    var voice = args[0];
    var param = args[1];
    var value = args[2];
    if (param.isNil || value.isNil, {
      logger.error("Invalid fx parameter message arguments");
      ^nil;
    });
    this.action303Voice(voice, \fx, { | synthFX |
      var paramName = synth303FXParamMap[param];
      var scaledValue = synth303FXScaleFuncs[paramName].value(value);
      logger.debug("Setting synth % fx % to % -> %".format(voice, paramName, value, scaledValue));
      synthFX.set(paramName, scaledValue);
    });
  }

  trigger808 { |args|
    var sound = switch(args[0],
      0, { \kick },
      1, { \snare },
      2, { \hat },
    );
    if (sound.notNil, {
      logger.debug("Triggering 808 -> %".format(sound));
      this.action808Drum(sound, {|drumSynth|
        drumSynth.set(\t_trig, 1);
      });
    });
  }

  set808Param { |args|
    var paramNum, value, paramName, sound;
    paramNum = args[0];
    value = args[1];
    if (paramNum.isNil || value.isNil, {
      logger.error("Invalid 808 parameter message arguments");
      ^nil;
    });
    paramName = drum808ParamMap[paramNum];
    sound = drum808SoundParamMap[paramNum];
    this.action808Drum(sound, {|drumSynth|
      var scaledValue = drum808ScaleFuncs[paramNum].value(value);
      logger.debug("Setting 808 % % to % -> %".format(sound, paramName, value, scaledValue));
      drumSynth.set(paramName, scaledValue)
    });
  }

  set808FXParam { |args|
    var param = args[0];
    var value = args[1];
    if (param.isNil || value.isNil, {
      logger.error("Invalid 808 FX parameter message arguments");
      ^nil;
    });
    logger.debug("Setting 808 FX param");
  }

  setupParamMaps {
    synth303ParamMap = Dictionary.new;
    synth303ScaleFuncs = Dictionary.new;
    synth303FXParamMap = Dictionary.new;
    synth303FXScaleFuncs = Dictionary.new;
    drum808ParamMap = Dictionary.new;
    drum808SoundParamMap = Dictionary.new;
    drum808ScaleFuncs = Dictionary.new;
    drum808FXParamMap = Dictionary.new;
    drum808FXScaleFuncs = Dictionary.new;
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

    this.add808Param(0, \kick, \decay, {|v| (v/35);});
  }

  addSynthParam { | n, name, func |
    synth303ParamMap.put(n, name);
    synth303ScaleFuncs.put(name, func);
  }

  addSynthFXParam { | n, name, func |
    synth303FXParamMap.put(n, name);
    synth303FXScaleFuncs.put(name, func);
  }

  add808Param { | n, sound, name, func |
    drum808ParamMap.put(n, name);
    drum808SoundParamMap.put(n, sound);
    drum808ScaleFuncs.put(n, func);
  }

  add808FXParam { | n, name, func |
    drum808FXParamMap.put(n, name);
    drum808FXScaleFuncs.put(name, func);
  }

  addGlobalFXParam { | n, name, func |
    globalFXParamMap.put(n, name);
    globalFXScaleFuncs.put(name, func);
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

    SynthDef(\narwhalKick, { arg out, t_trig=0, amp=0.5, decay=0.25;
      var amp_env, phase_env, phase, freq;

      freq = 10.rand + 90;

      amp_env   = EnvGen.ar(Env.perc(1e-6,decay), t_trig);
      phase_env = EnvGen.ar(Env.perc(1e-6,0.125), t_trig);

      phase = SinOsc.ar(20,0,pi) * phase_env;
      Out.ar(out, SinOsc.ar([freq,1.01*freq],phase) * amp_env * amp);
    }).add;

    SynthDef(\narwhalSnare, { arg out, t_trig=0, amp=0.5;
      var amp_env, cut_freq, dur;

      cut_freq = 3000;
      dur = [0.0625, 0.125, 0.25].choose;

      amp_env = EnvGen.ar(Env.perc(1e-6, dur), t_trig);
      Out.ar(out, LPF.ar( {WhiteNoise.ar(WhiteNoise.ar)}.dup * amp_env, cut_freq ) * amp);
    }).add;

    SynthDef(\narwhalHat, { arg out, t_trig=0, amp=0.5;
      var amp_env, cut_freq, dur;

      cut_freq = 6000;
      dur = [0.0625, 0.125, 0.25].choose;

      amp_env = EnvGen.ar(Env.perc(1e-7, dur), t_trig);
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

    drum808Sounds = Dictionary.new;
    drum808Sounds.put(\kick, Synth(\narwhalKick, [\out, globalBus]));
    drum808Sounds.put(\snare, Synth(\narwhalSnare, [\out, globalBus]));
    drum808Sounds.put(\hat, Synth(\narwhalHat, [\out, globalBus]));

    synth303Voices = voiceCount.collect { | c |
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

