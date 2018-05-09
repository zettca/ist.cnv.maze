all: server lb

server:
	javac `find MazeRunner/src/main/java -name *.java`

lb:
	javac `find LoadBalancer/src/main/java -name *.java`

clean:
	rm -rf `find . -name *.class`

run_server: server
	java MazeRunner.src.main.java.pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.WebServer

run_lb: lb
	java java LoadBalancer.src.main.java.ist.cnv.maze.loadbalancer.BalancerLauncher
