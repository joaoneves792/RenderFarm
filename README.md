# RenderFarm

The project is composed of 3 Components each in its own folder:
-WebServer
-LB
-MetricsManager

The MetricsManager class is a compile dependency of both the WebServer and the LoadBalancer.

The instrumentation code is located in ./WebServer/BIT/samples/MethodCallsCount.java

To compile the project run make on the base folder, this will compile all the Components 
(and includes the instrumentation of the WebServers code).

To execute the WebServer locally run make run-WS. (the webserver will run on port 8000)
To execute the LoadBalancer locally run make run-LB. (the loadBalancer will run on port 8001)

In order for the compilation to be successfull java-aws-sdk version 1.11.128 must be installed in
/opt/aws-java-sdk

The ./boot_script.sh and ./boot_script_lb.sh are scripts designed to bootstrap the project 
on EC2 Instances.

There is also a ./Client folder that contains code used to test the project during development.
