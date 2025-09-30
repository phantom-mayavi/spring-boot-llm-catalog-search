# Spring Boot LLM Catalog Search

A Spring Boot application that provides intelligent product catalog search capabilities using Large Language Models (LLMs) and semantic search with vector embeddings. The application leverages Google's Gemini API for generating embeddings and performs similarity-based search using cosine similarity.

## Features

- **Product Catalog Management**: Load and manage product data from CSV files
- **Semantic Search**: Intelligent search using vector embeddings and cosine similarity
- **RESTful API**: Clean REST endpoints for product retrieval and search
- **Automatic Embedding Generation**: Embeddings are generated at startup for all products
- **Cross-Origin Support**: CORS enabled for frontend integration

## Tech Stack

- **Java 17** - Programming language
- **Spring Boot 3.1.5** - Application framework
- **Spring Web** - REST API development
- **Spring WebFlux** - Reactive web framework
- **Jackson** - JSON processing
- **Lombok** - Code generation and boilerplate reduction
- **Google Gemini API** - Embedding generation (text-embedding-004 model)
- **Gradle** - Build tool

## Prerequisites

- Java 17 or higher
- Gradle (or use the included Gradle wrapper)
- Google Gemini API key

## Setup Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/phantom-mayavi/spring-boot-llm-catalog-search.git
cd spring-boot-llm-catalog-search
```

### 2. Configure Gemini API Key

Set your Google Gemini API key as an environment variable:

```bash
export GEMINI_API_KEY=your_gemini_api_key_here
```

Alternatively, you can set it in your IDE's run configuration or add it to your system environment variables.

### 3. Build the Project

Using Gradle wrapper (recommended):

```bash
./gradlew build
```

Or using installed Gradle:

```bash
gradle build
```

### 4. Run the Application

Using Gradle wrapper:

```bash
./gradlew bootRun
```

Or using installed Gradle:

```bash
gradle bootRun
```

The application will start on `http://localhost:8080` by default.

## API Documentation

### GET /search

Enhanced semantic search endpoint with filtering, pagination and sorting capabilities.

**Parameters:**
- `q` (required): Search query string (cannot be empty)
- `category` (optional): Filter by product category  
- `priceMin` (optional): Minimum price filter (decimal, must be non-negative)
- `priceMax` (optional): Maximum price filter (decimal, must be non-negative, must be >= priceMin)
- `page` (optional): Page number for pagination (default: 0, must be >= 0)
- `size` (optional): Number of results per page (default: 10, range: 1-50)
- `sort` (optional): Sort order - "score" (default) or "price"

**Rate Limiting:** 30 requests per minute per IP address.

**Success Response (200):**
```json
{
  "items": [
    {
      "id": 1,
      "name": "Product Name",
      "description": "Product description",
      "price": 99.99,
      "category": "Electronics",
      "score": 0.95
    }
  ],
  "total": 150,
  "page": 0,
  "size": 10,
  "hasNext": true
}
```

**Error Responses:**

**Validation Error (400):**
```json
{
  "error": "validation_error",
  "details": "Query parameter 'q' is required and cannot be empty",
  "status": 400
}
```

**Rate Limit Exceeded (429):**
```json
{
  "error": "rate_limited", 
  "details": "Rate limit exceeded. Maximum 30 requests per minute allowed.",
  "status": 429
}
```

**Internal Server Error (500):**
```json
{
  "error": "internal_error",
  "details": "An unexpected error occurred", 
  "status": 500
}
```

**Common Validation Errors:**
- Empty query: `"Query parameter 'q' is required and cannot be empty"`
- Invalid page: `"Page parameter must be non-negative"`
- Invalid size: `"Size parameter must be between 1 and 50"`
- Invalid price range: `"PriceMin cannot be greater than priceMax"`
- Invalid sort: `"Sort parameter must be 'score' or 'price'"`

### Get All Products

Retrieve products from the catalog with optional limit parameter.

**Endpoint:** `GET /products`

**Parameters:**
- `limit` (optional): Number of products to return (default: 10, max: 100)

**Example Request:**
```bash
curl -X GET "http://localhost:8080/products?limit=5"
```

**Example Response:**
```json
{
  "products": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Wireless Bluetooth Headphones",
      "description": "High-quality wireless headphones with noise cancellation",
      "price": 99.99,
      "category": "Electronics",
      "embeddings": [0.1234, -0.5678, 0.9012, ...]
    }
  ],
  "limit": 5,
  "returned": 1,
  "total": 50
}
```

### Get Product Embeddings

Retrieve embeddings for products with detailed information about vector dimensions.

**Endpoint:** `GET /products/embeddings`

**Parameters:**
- `limit` (optional): Number of embeddings to return (default: 5, max: 50)

**Example Request:**
```bash
curl -X GET "http://localhost:8080/products/embeddings?limit=3"
```

**Example Response:**
```json
{
  "embeddings": {
    "550e8400-e29b-41d4-a716-446655440000": {
      "productId": "550e8400-e29b-41d4-a716-446655440000",
      "dimension": 768,
      "vector": [0.1234, -0.5678, 0.9012, ...],
      "preview": [0.1234, -0.5678, 0.9012, 0.3456, -0.7890]
    }
  },
  "limit": 3,
  "returned": 1,
  "total": 50
}
```

## Migration Notes

**Important:** The legacy `/products/search` endpoint has been removed in favor of the enhanced `/search` endpoint. If you were using the old endpoint, please migrate to `/search` which offers:

- Enhanced filtering capabilities (category, price range)
- Pagination support
- Sorting options
- Input validation and better error handling
- Rate limiting for production use

**Migration Example:**
```bash
# Old (removed): GET /products/search?q=laptop&limit=10
# New (recommended): GET /search?q=laptop&size=10&page=0
```

## Embeddings and AI Integration

This application uses Google's Gemini API to generate vector embeddings for product descriptions. The embeddings enable semantic search capabilities that go beyond simple keyword matching.

### Key Features:

- **Automatic Embedding Generation**: Product embeddings are generated at application startup
- **L2 Normalization**: Embeddings are normalized for consistent similarity calculations
- **Cosine Similarity**: Search results are ranked by cosine similarity scores
- **Gemini API Integration**: Leverages Google's text-embedding-004 model for high-quality embeddings
- **Batch Processing**: Embeddings are processed in batches of 100 for efficiency

### Important Notes:

⚠️ **Gemini API Key Required**: You must obtain a valid Google Gemini API key and set it as an environment variable (`GEMINI_API_KEY`) for the embedding functionality to work.

⚠️ **Startup Time**: The first startup may take longer as embeddings are generated for all products in the catalog.

⚠️ **API Limits**: Be aware of Google Gemini API rate limits and quotas when using the application.

## Project Structure

```
src/
├── main/
│   ├── java/com/ai/catalogsearch/
│   │   ├── CatalogSearchApplication.java     # Main application class
│   │   ├── config/
│   │   │   ├── DataLoader.java               # CSV data loading configuration
│   │   │   └── WebConfig.java                # CORS configuration
│   │   ├── controller/
│   │   │   └── ProductController.java        # REST API endpoints
│   │   ├── embeddings/
│   │   │   ├── EmbeddingsClient.java         # Embeddings interface
│   │   │   └── GeminiEmbeddingsClient.java   # Gemini API implementation
│   │   ├── model/
│   │   │   └── Product.java                  # Product entity
│   │   ├── repository/
│   │   │   └── ProductRepository.java        # Data access layer
│   │   └── service/
│   │       ├── EmbeddingService.java         # Embedding management
│   │       └── SearchService.java            # Search logic
│   └── resources/
│       ├── application.yml                   # Application configuration
│       └── data/
│           └── products.csv                  # Sample product data
```

## Configuration

The application can be configured through `application.yml`:

```yaml
server:
  port: 8080

gemini:
  embeddings:
    base-url: https://generativelanguage.googleapis.com
    api-key: ${GEMINI_API_KEY:your-api-key-here}
    model: text-embedding-004
    batch-size: 100
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
