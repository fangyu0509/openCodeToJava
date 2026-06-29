.PHONY: build test run clean docker docker-run

build:
	mvn package -DskipTests

test:
	mvn test

run:
	mvn spring-boot:run

clean:
	mvn clean

docker:
	docker build -t opencode .

docker-run: docker
	docker run -p 8080:8080 \
		-e OPENAI_API_KEY \
		-e ANTHROPIC_API_KEY \
		-v $$PWD:/workspace \
		opencode

vscode:
	cd vscode-opencode && npm run compile
