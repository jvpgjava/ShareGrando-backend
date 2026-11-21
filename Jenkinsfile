pipeline {
  agent any

  environment {
    DEPLOY_USER = 'jgrando'
    DEPLOY_HOST = '127.0.0.1'
    SSH_KEY = '/opt/jenkins-home/.ssh/id_ed25519_deploy'
    API_PORT = '8080'
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    disableConcurrentBuilds()
    skipDefaultCheckout()
    timestamps()
    timeout(time: 20, unit: 'MINUTES')
  }

  triggers {
    githubPush()
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Validate and build') {
      steps {
        sh 'mvn -B -ntp clean verify'
      }
    }

    stage('Deploy') {
      steps {
        sh '''
          set -eu
          case "${BUILD_NUMBER:-}" in
            ''|*[!0-9]*) echo 'BUILD_NUMBER must be numeric' >&2; exit 1 ;;
          esac

          deploy_root='/var/www/jayshare/backend'
          release="$deploy_root/releases/$BUILD_NUMBER"
          current="$deploy_root/current.jar"
          next_link="$deploy_root/.current.$BUILD_NUMBER.next"
          jar='target/jayshare-backend-1.0.0.jar'
          previous=$(readlink "$current" 2>/dev/null || true)

          remote() {
            ssh -i "$SSH_KEY" \
              -o StrictHostKeyChecking=accept-new \
              -o IdentitiesOnly=yes \
              -o BatchMode=yes \
              "$DEPLOY_USER@$DEPLOY_HOST" "$1"
          }

          rollback() {
            echo 'Backend health check failed; restoring previous release' >&2
            if [ -n "$previous" ]; then
              ln -s "$previous" "$next_link"
              mv -Tf "$next_link" "$current"
              remote 'sudo -n /usr/bin/systemctl restart jayshare-api.service'
            fi
            exit 1
          }

          test -f "$jar"
          test ! -e "$release"
          install -d -m 0755 "$release"
          install -m 0644 "$jar" "$release/app.jar"

          ln -s "releases/$BUILD_NUMBER/app.jar" "$next_link"
          mv -Tf "$next_link" "$current"

          if ! remote 'sudo -n /usr/bin/systemctl restart jayshare-api.service'; then
            rollback
          fi

          attempt=0
          while [ "$attempt" -lt 20 ]; do
            if remote "curl --fail --silent --max-time 2 http://127.0.0.1:$API_PORT/internal/health >/dev/null"; then
              echo "Backend deployed to $release"
              exit 0
            fi
            attempt=$((attempt + 1))
            sleep 2
          done

          rollback
        '''
      }
    }
  }
}
