(

	s.options.numInputBusChannels = 0;

  s.quit;

	s.waitForBoot {
    ~narwhal = Narwhal.new.init(debug: true);
    s.sync;
    ~narwhal.setup(s, 49162, 4)
	}

)
