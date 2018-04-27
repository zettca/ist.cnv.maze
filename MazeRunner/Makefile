all:
	javac -encoding UTF-8 -cp . `find . -name *.java`

clean:
	rm -rf `find . -name *.class`
run: all
	java pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.WebServer
