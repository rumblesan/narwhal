(

	s.options.numInputBusChannels = 0;

  s.quit;

	s.waitForBoot {
    ~narwhal = Narwhal.new.init(debug: true);
    s.sync;
    ~narwhal.setup(s, 4);
    ~narwhal.setupOrcaOSC(49162);
	}

)
