pipeline {
    agent {
        label 'jenkins-agent'
    }
    
    environment {
        SONAR_PROJECT_KEY = "test-project"
        DOCKER_IMAGE = "sample-app"
        DOCKER_TAG = "latest"
    }
    
    stages {
        stage('Deploy to Kubernetes') {
            steps {
                container('kubectl') {
                    sh '''
                        echo "Deploying to Kubernetes..."
                        kubectl config set-cluster k8s-cluster --server=${KUBERNETES_SERVER} --insecure-skip-tls-verify
                        kubectl config set-credentials jenkins-user --token=${KUBERNETES_TOKEN}
                        kubectl config set-context k8s-cluster --cluster=k8s-cluster --user=jenkins-user --namespace=${K8S_NAMESPACE}
                        kubectl config use-context k8s-cluster
                        kubectl apply -f deployments/nginx-deployment.yaml
                        kubectl apply -f services/nginx-service.yaml
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "Deployment successful!"
        }
        failure {
            echo "Deployment failed!"
        }
    }
}