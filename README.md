# picsforyou.cloud — Cloud Storage API

Cloud storage platform with user management, subscription plans, Stripe payments, and Google Cloud Storage integration.

## Architecture

```
picsforyou-cloud/
├── backend/          # Java Spring Boot REST API (port 3000)
└── frontend/         # React + TypeScript playground (port 5173)
```

## Backend

Spring Boot 3 application with:

- **Auth**: Email/password registration, email verification (2-step), session management
- **Storage**: Google Cloud Storage integration for file upload/download/delete
- **Plans**: Free (30 MB), Base (€5.99 — 5 GB), Professional (€20 — 20 GB), Custom
- **Payments**: Stripe Checkout for plan upgrades/downgrades with differential pricing
- **Email**: HTML emails via Aruba SMTP (info@picsforyou.cloud)

### Requirements

- Java 21+
- MySQL 8+
- Google Cloud Storage bucket + service account key
- Stripe account (test or live keys)

### Configuration

Set environment variables:

```bash
export STRIPE_SECRET_KEY=rk_test_...       # Stripe restricted key
export MAIL_PASSWORD=...                    # SMTP password
export GCS_CREDENTIALS_PATH=path/to/key.json # GCS service account
export JWT_SECRET=change-me                 # JWT signing secret
```

### Run

```bash
cd backend
mvn package -DskipTests
java -jar target/cloud-storage-api-1.0.0.jar
```

## Frontend

React + TypeScript + Vite playground for testing the API.

### Run

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` requests to `http://localhost:3000`.

## API Endpoints

### Auth
- `POST /api/v1/auth/signup` — Register
- `POST /api/v1/auth/verify` — Verify email
- `POST /api/v1/auth/login` — Login
- `GET /api/v1/auth/me` — Current user
- `POST /api/v1/auth/logout` — Logout

### Plans
- `GET /api/v1/plans` — List plans
- `POST /api/v1/plans/select` — Select/change plan (session)
- `POST /api/v1/plans/assign` — Assign plan (email, for signup flow)
- `POST /api/v1/plans/create-checkout` — Create Stripe Checkout session
- `GET /api/v1/plans/checkout-success` — Stripe redirect handler
- `POST /api/v1/plans/custom-request` — Submit custom plan request

### Storage
- `POST /api/v1/storage/upload` — Upload file
- `GET /api/v1/storage/files` — List files
- `GET /api/v1/storage/files/{id}` — Download file
- `DELETE /api/v1/storage/files/{id}` — Delete file

## License

Private — All rights reserved.
