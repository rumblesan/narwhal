
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
      2, { \closedHat },
      3, { \openHat },
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
    this.addSynthParam(2, \resonance, {|v| (7 / (v + 2).squared);});
    this.addSynthParam(3, \sustain, {|v| ((v/35) * 3) + 0.1;});
    this.addSynthParam(4, \decay, {|v| ((v/35) * 3) + 0.1;});
    this.addSynthParam(5, \envelope, {|v| (v * 60);});
    this.addSynthParam(6, \volume, {|v| (v/35) * 1.1;});
    this.addSynthParam(7, \glide, {|v| ((v + 0.1)/30).squared;});

    this.addSynthFXParam(0, \delayTime, {|v| (v/35);});
    this.addSynthFXParam(1, \delayFeedback, {|v| (v/35);});
    this.addSynthFXParam(2, \reverbMix, {|v| (v/35);});
    this.addSynthFXParam(3, \reverbRoom, {|v| (v/35);});
    this.addSynthFXParam(4, \reverbDamping, {|v| (v/35);});
    this.addSynthFXParam(5, \distortion, {|v| (v/35);});
    this.addSynthFXParam(6, \gain, {|v| (v/5) + 0.9;});

    this.addGlobalFXParam(0, \reverbMix, {|v| (v/35);});
    this.addGlobalFXParam(1, \reverbRoom, {|v| (v/35);});
    this.addGlobalFXParam(2, \reverbDamping, {|v| (v/35);});

    this.add808Param(0, \kick, \freq, {|v| (v * 6);});
    this.add808Param(1, \kick, \decay, {|v| v/35;});
    this.add808Param(2, \kick, \tone, {|v| v/35;});
    this.add808Param(3, \snare, \duration, {|v| (v + 1)/35;});
    this.add808Param(4, \snare, \cut_freq, {|v| (v + 1) * 300;});
    this.add808Param(5, \closedHat, \duration, {|v| (v + 1)/35;});
    this.add808Param(6, \closedHat, \cut_freq, {|v| (v + 1) * 300;});
    this.add808Param(7, \openHat, \duration, {|v| (v + 1)/35;});
    this.add808Param(8, \openHat, \cut_freq, {|v| (v + 1) * 300;});
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

    SynthDef(\narwhalKick, { arg out, t_trig=0, amp=0.5, freq=90, decay=0.25, tone=0.125;
      var tuned = Rand(-5, 5) + freq;

      var amp_env   = EnvGen.ar(Env.perc(1e-6, decay), t_trig);
      var phase_env = EnvGen.ar(Env.perc(1e-6, tone), t_trig);

      var phase = SinOsc.ar(20,0,pi) * phase_env;
      Out.ar(out, SinOsc.ar(tuned, phase) * amp_env * amp);
    }).add;

    SynthDef(\narwhalSnare, { arg out, t_trig=0, amp=0.5, cut_freq=3000, duration=0.125;
      var amp_env = EnvGen.ar(Env.perc(1e-6, duration), t_trig);
      Out.ar(out, LPF.ar( {WhiteNoise.ar(WhiteNoise.ar)}.dup * amp_env, cut_freq ) * amp);
    }).add;

    SynthDef(\narwhalClosedHat, { arg out, t_trig=0, amp=0.5, duration=0.125, cut_freq=6000;
      var amp_env = EnvGen.ar(Env.perc(1e-7, duration), t_trig);
      Out.ar(out, HPF.ar( {WhiteNoise.ar}.dup * amp_env, cut_freq ) * amp / 4);
    }).add;

    SynthDef(\narwhalOpenHat, { arg out, t_trig=0, amp=0.5, duration=0.125, cut_freq=6000;
      var amp_env = EnvGen.ar(Env.perc(1e-7, duration + 0.7), t_trig);
      Out.ar(out, HPF.ar( {WhiteNoise.ar}.dup * amp_env, cut_freq ) * amp / 4);
    }).add;

    SynthDef(\narwhalGlobalFX, {
      arg in, out, reverbMix=0.33, reverbRoom=0.5, reverbDamping=0.5;
      var inputSignal = In.ar(in, 2);
      var reverbed = FreeVerb2.ar(inputSignal[0], inputSignal[1], reverbMix, reverbRoom, reverbDamping);
      var compressed = Compander.ar(reverbed, reverbed, 0.9, 1, 0.3, 0.002, 0.1) * 1.1;
      Out.ar(out, compressed.softclip);
    }).add;

    SynthDef(\narwhalSynth, {
      arg out, freq=440, wave=0, cutoff=100, resonance=0.2,
          sustain=0, decay=1.0, glide=0.01, envelope=1000, t_trig=0, volume=0.2;
      var filEnv, volEnv, waves, voice;

      volEnv = EnvGen.ar(
        Env.new([10e-10, 1, 1, 10e-10], [0.01, sustain, decay], 'exp'),
        t_trig);
      filEnv = EnvGen.ar(
        Env.new([10e-10, 1, 10e-10], [0.01, decay], 'exp'),
        t_trig);
      waves = [Saw.ar(Lag.kr(freq, glide), volEnv), Pulse.ar(Lag.kr(freq, glide), 0.5, volEnv)];
      voice = RLPF.ar(
        Select.ar(wave, waves),
        cutoff + (filEnv * envelope),
        resonance
      ).dup;

      Out.ar(out, voice * volume);
    }).add;

    SynthDef(\narwhalSynthFX, {
      arg in, out, delayTime=0.25, delayFeedback=0.2, reverbMix=0, reverbRoom=0.5, reverbDamping=0.5, gain=1.3, distortion=0.1;
      var inputSignal, shaped, distorted, compressed, haasDelay, haasChannel, haased, delayed, reverbed;
      inputSignal = In.ar(in, 2);

      shaped = Shaper.ar(shaperBuffer, inputSignal, 0.5);
      distorted = ((((shaped * distortion) + (inputSignal * (1 - distortion))) * gain).distort) * 2;

      compressed = Compander.ar(distorted, distorted, 0.3);

      haasChannel = [\left, \right].choose;
      haasDelay = (5 + 15.rand)/1000;

      haased = if (haasDelay == \left, {
        [DelayN.ar(compressed[0], 0.1, haasDelay), compressed[1]];
      }, {
        [compressed[0], DelayN.ar(compressed[1], 0.1, haasDelay)];
      });

      delayed = haased + (LocalIn.ar(2) * delayFeedback);

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
    drum808Sounds.put(\closedHat, Synth(\narwhalClosedHat, [\out, globalBus]));
    drum808Sounds.put(\openHat, Synth(\narwhalOpenHat, [\out, globalBus]));

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

