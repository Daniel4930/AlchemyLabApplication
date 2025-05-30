mvn clean install

aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 022791083578.dkr.ecr.us-east-1.amazonaws.com

docker build -t gen-chem-application .

docker tag gen-chem-application:latest 022791083578.dkr.ecr.us-east-1.amazonaws.com/gen-chem-application:latest

docker push 022791083578.dkr.ecr.us-east-1.amazonaws.com/gen-chem-application:latest