# Sweet Market Milestone 8 Web Demo Handoff

## What This Milestone Adds

Milestone 8 adds a Vite React frontend for the Sweet Market JPA demo. The web app supports product browsing, signup/login, product registration/editing, buyer order progress, seller sales management, seller settlement lookup, and an admin settlement batch console.

## Backend Local Run

The backend lives in `backend` and uses Java 21.

Start PostgreSQL:

```powershell
cd backend
docker compose up -d
```

Run the backend with demo seed data:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
$env:SPRING_PROFILES_ACTIVE='local'
.\gradlew.bat bootRun
```

The backend listens on `http://localhost:8080`. CORS is configured for the default Vite dev server origin, `http://localhost:5173`.

## Demo Seed Data

`DemoDataInitializer` runs only with the `local` or `dev` Spring profile. It exits without reseeding if `admin@example.com` already exists.

All seeded accounts use password `password123`:

- Admin: `admin@example.com`
- Sellers: `seller1@example.com`, `seller2@example.com`
- Buyers: `buyer1@example.com`, `buyer2@example.com`

The seed data includes visible products and orders across CREATED, PAID, SHIPPING, DELIVERED, CONFIRMED-unsettled, and CONFIRMED-settled states so buyer, seller, settlement, and admin batch screens have useful demo data.

## Web Local Run

The frontend lives in `web`.

```powershell
cd web
npm install
npm run dev
```

Open `http://localhost:5173`.

By default the web app calls `http://localhost:8080`. To point it elsewhere, set `VITE_API_BASE_URL` before starting Vite.

Build verification:

```powershell
cd web
npm run build
```

## Suggested Demo Flow

1. Open `http://localhost:5173` and browse the seeded market products.
2. Log in as `buyer1@example.com` / `password123`, create an order from a product detail page, then use `내 주문` to approve payment, start/complete delivery, and confirm purchase as each state allows.
3. Log in as `seller1@example.com` / `password123`, use `내 판매` to inspect seller listings, edit a product, or hide a listing.
4. Use `정산` as a seller to review generated settlement records.
5. Log in as `admin@example.com` / `password123`, open `관리자`, run the settlement batch, and inspect recent execution history/detail.

## Backend Test Command

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

JUnit `@Test` method names must stay Korean with underscores.
