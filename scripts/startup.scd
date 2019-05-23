(

	s.options.numInputBusChannels = 0;

  s.quit;

	s.waitForBoot {
    ~narwhal = Narwhal.new.init(debug: true);
    s.sync;
    ~narwhal.setup(49162, 1)
	}

)
