all: server lb

server:
	javac `find MazeRunner/src/main/java -name *.java`

lb:
	javac `find LoadBalancer/src/main/java -name *.java`

run_server: server
	java -cp MazeRunner/src/main/java pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.WebServer

run_lb: lb
	cd LoadBalancer/src/main/java && java ist.cnv.maze.loadbalancer.AutoBalancer

clean:
	rm -rf `find . -name *.class`
