all: server lb

build:
	javac BIT/BIT/highBIT/*.java BIT/BIT/lowBIT/*.java
	javac BIT/samples/*.java

server:
	javac `find MazeRunner/src/main/java -name *.java`

lb:
	javac `find LoadBalancer/src/main/java -name *.java`

instrument:
	cd MazeRunner/src/main/java/pt/ulisboa/tecnico/meic/cnv/mazerunner/maze/ && java MethodCount RobotController.class

run_server:
	java -cp MazeRunner/src/main/java pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.WebServer

run_server_instrumented: clean build server instrument
	java -cp MazeRunner/src/main/java pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.WebServer

run_lb: lb
	cd LoadBalancer/src/main/java && java ist.cnv.maze.loadbalancer.AutoBalancer

clean:
	rm -rf `find MazeRunner -name *.class`
