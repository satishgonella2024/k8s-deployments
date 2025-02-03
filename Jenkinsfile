pipeline {
    agent {
        kubernetes {
            yaml '''
            apiVersion: v1
            kind: Pod
            spec:
              containers:
              - name: kubectl
                image: bitnami/kubectl:latest
                command:
                - cat
                tty: true
            '''
        }
    }

    environment {
        KUBERNETES_SERVER = "https://kubernetes.default.svc.cluster.local"
        KUBERNETES_TOKEN = credentials('KUBERNETES_TOKEN')
        K8S_NAMESPACE = "k8s-deployments"
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