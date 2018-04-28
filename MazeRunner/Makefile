all: server lb as

server:
	javac `find pt -name *.java`

lb:
	javac `find ist/cnv/loadbalancer -name *.java`

as:
	javac `find ist/cnv/autoscaler -name *.java`

clean:
	rm -rf `find . -name *.class`

run_server: server
	java pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.WebServer

run_lb: lb
	java ist.cnv.loadbalancer.BalancerLauncher

run_as: as
	java ist.cnv.autoscaler.AutoScaler
