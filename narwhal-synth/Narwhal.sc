
Narwhal {

  var logger;
  var synths;
  var fx;

  var tonic;
  var scale;

  init { | s, debug = false |
    logger = NarwhalLogger.new.init(debug);
    this.defineSynths;
    tonic = 36;
    scale = Scale.minor;
  }

  setup { | oscInPort, voices = 4 |
    this.setupAudio(voices);
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

    OSCFunc({ |msg|
      this.playSynth(msg[1], msg[2], msg[3]);
    }, '/n', NetAddr("localhost"), oscPort);

  }

  playSynth { | n, octave, note |
    if (note.notNil && octave.notNil, {
      this.actionSynth(n, { | synth |
        logger.debug("Playing synth %".format(n));

        Routine {
          synth.set(\freq, scale.degreeToFreq(note, tonic.midicps, octave));
          synth.set(\gate, 1);
          0.01.wait;
          synth.set(\gate, 0);
        }.play;
      });
    }, { logger.error("No note or octave given to play") });
  }

  defineSynths {
    logger.log("Defining synths");

    SynthDef(\narwhalFX, {
      arg in, out=0;
      Out.ar(out, In.ar(in));
    }).add;

    // copied from https://sccode.org/1-4Wy
    SynthDef(\narwhalSynth, {
      arg out=0, freq=440, wave=0, ctf=100, res=0.2,
          sus=0, dec=1.0, env=1000, gate=0, vol=0.2;
      var filEnv, volEnv, waves;

      volEnv = EnvGen.ar(
        Env.new([10e-10, 1, 1, 10e-10], [0.01, sus, dec], 'exp'),
        gate);
      filEnv = EnvGen.ar(
        Env.new([10e-10, 1, 10e-10], [0.01, dec], 'exp'),
        gate);
      waves = [Saw.ar(freq, volEnv), Pulse.ar(freq, 0.5, volEnv)];

      Out.ar(out, RLPF.ar(
        Select.ar(wave, waves),
        ctf + (filEnv * env),
        res
      ).dup * vol);
    }).add;

  }

  setupAudio { | voiceCount |
    logger.log("Setting up audio");

    fx = Synth(\narwhalFX, [\in, 1]);

    synths = voiceCount.collect { | c |
      Synth(\narwhalSynth, [\out, 1]);
    };
  }

}

