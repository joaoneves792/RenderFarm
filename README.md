# RenderFarm

## Run on your local machine

Make sure you are running Java 7

make

make run

Test it with:

curl "http://localhost:8000/r.html?f=test05.txt&sc=400&sr=300&wc=400&wr=300&coff=0&roff=0"

## WebServer

Compile raytracer with make

Compile WebServer with:

javac WebServer.java -cp ./raytracer-master/src/

Run Webserver with:

java -cp ./raytracer-master/src:. WebServer

Examples of valid requests:

http://localhost:8000/r.html?f=test05.txt&sc=400&sr=300&wc=400&wr=300&coff=0&roff=0

http://localhost:8000/test

## Instrument code with BIT
1. Add BIT tool path to classpath:
`export CLASSPATH="<path-to-project>/WebServer/BIT:<path-to-project>/WebServer/BIT/samples:./"`
2. Make sure you are running java 7. `java -version`
3. Compile instrumentation tools in samples folder. `javac *.java`
4. Compile WebServer with instrumentation tool: `java Icount <path-to-WebServer> <path-to-output-folder>`
5. Run instrumented WebServer class: `java -cp <project-path>/WebServer/BIT/samples:<project-path>/WebServer/raytracer-master/src:. WebServer`
