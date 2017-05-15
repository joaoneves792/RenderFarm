all:
	make -C ./WebServer/
	make -C ./LB/
	make -C ./MetricsManager/

run-LB:
	make run -C ./LB/

run-WS:
	make run -C ./WebServer/

clean: 
	-make clean -C ./WebServer/
	-make clean -C ./LB/
	-make clean -C ./MetricsManager/

