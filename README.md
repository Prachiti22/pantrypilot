# PantryPilot

AI-assisted shared kitchen inventory. Final project for CS6103 Introduction to Java (Fall 2026).

Team: Rohit Shidid, Prachiti Kulkarni, Vishvaksen Pendyala.

PantryPilot fills a household's inventory from a receipt scan or a grocery photo, and takes ingredients out when you paste the recipe link you cooked from. The AI only proposes changes. The user confirms or edits them before anything is applied.

## What is in this repository

One Spring Boot server (Java 21) split into three modules, one per team member. The modules only talk through the types in `shared`, and a test fails the build if one reaches into another's internals.

| Package | Owner | State |
|---|---|---|
| `com.pantrypilot.shared` | all | The team contract: interfaces, `ProposedChange`, events |
| `com.pantrypilot.ai` | Rohit | **Built**: receipt scan, grocery photos, recipe links, price estimator, threaded job pipeline |
| `com.pantrypilot.core` | Prachiti | **Placeholder**: in-memory catalog, unit conversion and review queue under `core.stub` |
| `com.pantrypilot.insights` | Vishvak | **Placeholder**: one endpoint under `insights.stub` showing the money-saved formula |

The placeholders exist so the AI module can run end to end. They are meant to be deleted as the real modules arrive.

## Run it

You need Java 21. Everything else is optional, and the status row on the test page shows what is available.

| Tool | Needed for | Install (macOS) |
|---|---|---|
| [Ollama](https://ollama.com) with a vision model | photos, receipt line parsing, price estimates, pages without recipe data | `ollama pull gemma4` |
| Tesseract | receipt scanning | `brew install tesseract` |
| yt-dlp | YouTube links | `brew install yt-dlp` |

```bash
./mvnw spring-boot:run
```

Then open <http://localhost:8090>. If Maven picks up a newer JDK and fails, point it at Java 21 first: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

Settings can be changed with environment variables:

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | `8090` | Server port |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama server |
| `PANTRYPILOT_VISION_MODEL` | `gemma4` | Any Ollama model with vision |
| `PANTRYPILOT_TEXT_MODEL` | `gemma4` | Any Ollama text model |
| `SEARXNG_URL` | empty | SearXNG instance for web price search; empty means ask the model |

The default database is in-memory H2, so data is lost on restart. For PostgreSQL: `docker compose up -d`, then `./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres`.

## Test it

```bash
./mvnw test
```

120 tests. They need no Ollama, no network and no yt-dlp, because those are replaced by fakes. The OCR tests run the real Tesseract and skip themselves if it is not installed.

To try the real thing, start the server and use the test page:

1. **Recipe link**: paste a recipe page such as `https://www.bbcgoodfood.com/recipes/best-spaghetti-bolognese-recipe`, set servings, press *Propose deduction*. Works without Ollama when the page has schema.org recipe data.
2. **Restaurant price**: type a dish, press *Estimate*, then correct the price and estimate again.
3. **Receipt**: upload a photo of a receipt.
4. **Groceries**: upload one to six photos. A local vision model takes 30 to 60 seconds per photo.

Each result lands in the review queue at the bottom of the page, where it can be confirmed or rejected.

## AI module API

The three POST endpoints start a background job and answer `202 Accepted` at once. Poll the job until its status is `SUCCEEDED` or `FAILED`.

| Method and path | Body | Purpose |
|---|---|---|
| `POST /api/ai/receipts` | multipart: `image`, `householdId`, `userId` | Scan a receipt |
| `POST /api/ai/photos` | multipart: `images` (1 to 6), `householdId`, `userId` | Recognise groceries |
| `POST /api/ai/recipe-links` | JSON: `url`, `servingsMade` (optional), `householdId`, `userId` | Deduction for a cooked recipe |
| `GET /api/ai/jobs/{id}` | | Job status and, when finished, the proposal |
| `GET /api/ai/jobs?householdId=` | | The household's 20 most recent jobs |
| `GET /api/ai/price-estimate?dish=&servings=&city=` | | Price of ordering the dish out |
| `PUT /api/ai/price-estimate` | JSON: `dish`, `city`, `pricePerServing` | Save the user's own price |
| `GET /api/ai/status` | | Which tools and models are available |

```bash
curl -X POST localhost:8090/api/ai/recipe-links -H 'Content-Type: application/json' \
  -d '{"url":"https://www.bbcgoodfood.com/recipes/best-spaghetti-bolognese-recipe","servingsMade":3,"householdId":1,"userId":1}'
curl localhost:8090/api/ai/jobs/1
```

## How the AI module works

- **Job pipeline** (`ai.internal.jobs`): a fixed thread pool with a bounded queue. A request records a job and returns; a worker thread does the work through a `CompletableFuture` with a timeout. Finishing a job locks its database row, so a worker and the timeout cannot both finish it and a late result is never submitted. When the queue is full the server answers 503.
- **Receipts** (`ai.internal.receipt`, `ai.internal.ocr`): grayscale, enlarge, deskew, then Tesseract through Tess4J. The text model turns lines like `ORG BNS 2LB` into ingredients. Unit prices are computed in Java from the line total. If the model is down, a layout-based parser takes over with low confidence.
- **Photos** (`ai.internal.photo`): each photo goes to the vision model on its own virtual thread, then the answers are merged. Within a photo, amounts of one ingredient are added. Across photos the largest wins, since photos of one haul overlap.
- **Recipe links** (`ai.internal.recipe`): schema.org recipe data is read with Jsoup and parsed without any model. Pages without it go to the text model. YouTube links are read with yt-dlp: the description's ingredient list first, then the model over the description, then the captions.
- **Prices** (`ai.internal.price`): the user's own price, else the cache, else the median of prices found through SearXNG, else the model's estimate.
- **Safety**: every number from a model is range-checked. Links to private network addresses are refused, on every redirect. Uploads are checked to be real images.

## For teammates: replacing a placeholder

1. Write your implementation of the interface in your own module (for example `CatalogService` in `core`).
2. Delete the matching class under `core.stub` or `insights.stub`.
3. Run `./mvnw test`. `ModularityTest` checks that modules still only use `shared`.

The AI module uses `CatalogService.resolve` and `PendingChangeService.submit`, and provides `PriceEstimator` and `RecipeExtractor`. In `ProposedLine`, `unitPrice` is the price of one unit: one item, one gram or one millilitre.

## Not done yet

- Stretch goals: photos while cooking, finished-dish photo with correction memory, Instagram links.
- Speech-to-text fallback (whisper.cpp) for videos without captions.
- The Flutter screens for capture and link paste. The test page stands in for them.
- Login. `householdId` and `userId` are plain request parameters until the core module adds JWT security.
- Photo storage (MinIO). Photos are processed in memory and not kept.
- The labelled evaluation sets and accuracy scripts from the proposal.

Known limits of what is built:

- Receipt scanning has only been tried on computer-drawn receipt images, not on phone photos of paper receipts. Sideways phone photos are not rotated, and HEIC images are not accepted.
- Web price search has been tested against sample SearXNG answers only, never a running SearXNG.
- The PostgreSQL profile has not been run.
- The models are called with `java.net.http.HttpClient` directly rather than through LangChain4j, and it has been run with `gemma4` rather than the Qwen2.5-VL and Llama 3.1 models named in the proposal.

## Libraries

Spring Boot, Spring Data JPA, Spring Modulith, Flyway, H2, PostgreSQL driver, Tess4J (Tesseract), Jsoup, JUnit 5, AssertJ. External tools: Ollama, Tesseract, yt-dlp.
