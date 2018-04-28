all: server lb

server:
	javac `find pt -name *.java`

lb:
	javac `find ist -name *.java`

clean:
	rm -rf `find . -name *.class`

run_server: server
	java pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.WebServer

run_lb: lb
	java ist.cnv.loadbalancer.BalancerLauncher
