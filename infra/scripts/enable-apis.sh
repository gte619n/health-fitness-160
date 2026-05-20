#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="health-fitness-160"
APIS=(
  run.googleapis.com
  cloudbuild.googleapis.com
  artifactregistry.googleapis.com
  firestore.googleapis.com
  secretmanager.googleapis.com
  iam.googleapis.com
  iamcredentials.googleapis.com
  health.googleapis.com
  logging.googleapis.com
  monitoring.googleapis.com
)

for api in "${APIS[@]}"; do
  echo "Enabling $api"
  gcloud services enable "$api" --project="$PROJECT_ID"
done
