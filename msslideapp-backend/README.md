# msslideapp-backend

## Setup

- Java 8 and Maven required.
- Configure Git remote by setting environment variables:
  - GIT_REMOTE_URL (e.g. https://github.com/youruser/yourrepo.git)
  - GIT_TOKEN (a personal access token that has repo:status, repo_deployment, repo, workflow permissions)
- Or modify `GitService` to use credentials from application.properties.

## Run

mvn clean package
java -jar target/msslideapp-backend-0.0.1-SNAPSHOT.jar

API:
- POST /api/upload (multipart form-data with key `file`)
- POST /api/approve (JSON { id, approver })
- POST /api/reject (JSON { id, approver })
- GET /api/history
