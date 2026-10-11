# Deployment guide

This repository contains a plain HTML/CSS/JavaScript frontend and a Java 17 + Javalin backend. Deploy them as two services.

## 1. Deploy the backend on Render

Use the repository root as the service root because the root `Dockerfile` copies both `backend/` and the default `data/` and `frontend/` assets. Prefer creating the service from the included `render.yaml` Blueprint. If creating a web service manually, select **Docker** and use `Dockerfile` at the repository root. Do not use `yarn`, `npm start`, or a Node.js runtime for the Java backend.

The container builds `backend/pom.xml` with Maven and Java 17. Render supplies the `PORT` environment variable at runtime; the Java application reads it. The health-check endpoint is `/api/health`. After deploy, open `https://YOUR-RENDER-SERVICE.onrender.com/api/health` and confirm it returns JSON.

## 2. Deploy the frontend on Vercel

Create a Vercel project from the same Git repository. Set the project root to the repository root and set the **Output Directory** to `frontend`. The project is static HTML/CSS/JavaScript; do not select a React/Vite framework and do not use `yarn start`. The included `vercel.json` also specifies `frontend` as the output directory. The deployed root should serve `frontend/index.html`.

If you instead set Vercel's Root Directory to `frontend`, do not keep the repository-root output-directory configuration active; use `.` as the output directory and ensure the Vercel project recognizes the config in that root. The simpler recommended setup is repository root + output directory `frontend`.

## 3. Connect the frontend to Render

After the backend is live, copy its base URL, for example `https://YOUR-RENDER-SERVICE.onrender.com`. In `frontend/config.js`, set `BACKEND_URL` to that exact origin (no trailing slash and no `/api` suffix), then commit and push the change so Vercel redeploys.

Alternatively, open the deployed frontend, click the **Backend Status** badge, and enter the Render URL. That setting is saved in the browser's local storage. You can also use `?backend=https://YOUR-RENDER-SERVICE.onrender.com` as a query parameter.

## 4. Troubleshooting

- A Vercel 404 usually means the project root/output directory is configured incorrectly or the latest deployment is not serving `frontend/index.html`. Check the Vercel deployment logs and project settings.
- If the page loads but says the backend URL is not configured, set `BACKEND_URL` as described above.
- If the backend is configured but browser requests fail, check that the Render service is live and inspect browser DevTools → Network/Console for CORS, HTTP, or API errors.
- On Render's free plan, the service may sleep while idle and take a short time to wake.
- Never use `localhost` as the production backend URL; it refers to the visitor's own device.

## 5. Local checks

Build the backend from the repository root with:

```bash
mvn -f backend/pom.xml clean package
```

Run it locally with:

```bash
java -jar backend/target/forest-fire-predictor-1.0.0.jar
```

Then check `http://localhost:7070/api/health`. If Maven is not installed locally, use the included Docker build or install Maven 3.9+ and JDK 17.
