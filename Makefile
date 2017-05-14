all:
	make -C ./WebServer/
	make -C ./LB/
	make -C ./MetricsManager/

clean: 
	-make clean -C ./WebServer/
	-make clean -C ./LB/
	-make clean -C ./MetricsManager/
