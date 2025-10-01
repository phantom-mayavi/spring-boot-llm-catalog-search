import React, { useState } from 'react';
import './App.css';

function App() {
  const [searchData, setSearchData] = useState({
    q: '',
    category: '',
    priceMin: '',
    priceMax: '',
    page: 0,
    size: 10,
    sort: 'score'
  });
  
  const [results, setResults] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setSearchData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleSearch = async (pageOverride = 0) => {
    if (!searchData.q.trim()) {
      setError('Please enter a search query');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const params = new URLSearchParams();
      params.append('q', searchData.q.trim());
      
      if (searchData.category) params.append('category', searchData.category);
      if (searchData.priceMin) params.append('priceMin', searchData.priceMin);
      if (searchData.priceMax) params.append('priceMax', searchData.priceMax);
      params.append('page', pageOverride.toString());
      params.append('size', searchData.size.toString());
      params.append('sort', searchData.sort);

      const response = await fetch(`${API_BASE_URL}/search?${params}`);
      
      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Search failed');
      }

      const data = await response.json();
      setResults(data);
      setSearchData(prev => ({ ...prev, page: pageOverride }));
    } catch (err) {
      setError(err.message);
      setResults(null);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    handleSearch(0);
  };

  const handlePageChange = (newPage) => {
    handleSearch(newPage);
  };

  const formatPrice = (price) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD'
    }).format(price);
  };

  const getSnippet = (description, maxLength = 150) => {
    if (!description) return '';
    return description.length > maxLength 
      ? description.substring(0, maxLength) + '...'
      : description;
  };

  return (
    <div className="App">
      <header className="App-header">
        <h1>🔍 Semantic Catalog Search</h1>
        <p>Discover products using AI-powered semantic search</p>
      </header>

      <main className="search-container">
        <form onSubmit={handleSubmit} className="search-form">
          <div className="search-row">
            <div className="search-input-group">
              <label htmlFor="q">Search Query *</label>
              <input
                type="text"
                id="q"
                name="q"
                value={searchData.q}
                onChange={handleInputChange}
                placeholder="e.g., comfortable running shoes, wireless headphones..."
                required
              />
            </div>
            
            <div className="search-input-group">
              <label htmlFor="category">Category</label>
              <input
                type="text"
                id="category"
                name="category"
                value={searchData.category}
                onChange={handleInputChange}
                placeholder="e.g., Electronics, Clothing"
              />
            </div>
          </div>

          <div className="search-row">
            <div className="search-input-group">
              <label htmlFor="priceMin">Min Price ($)</label>
              <input
                type="number"
                id="priceMin"
                name="priceMin"
                value={searchData.priceMin}
                onChange={handleInputChange}
                min="0"
                step="0.01"
                placeholder="0.00"
              />
            </div>
            
            <div className="search-input-group">
              <label htmlFor="priceMax">Max Price ($)</label>
              <input
                type="number"
                id="priceMax"
                name="priceMax"
                value={searchData.priceMax}
                onChange={handleInputChange}
                min="0"
                step="0.01"
                placeholder="1000.00"
              />
            </div>
            
            <div className="search-input-group">
              <label htmlFor="size">Results per page</label>
              <select
                id="size"
                name="size"
                value={searchData.size}
                onChange={handleInputChange}
              >
                <option value="5">5</option>
                <option value="10">10</option>
                <option value="20">20</option>
                <option value="50">50</option>
              </select>
            </div>
            
            <div className="search-input-group">
              <label htmlFor="sort">Sort by</label>
              <select
                id="sort"
                name="sort"
                value={searchData.sort}
                onChange={handleInputChange}
              >
                <option value="score">Relevance (Score)</option>
                <option value="price">Price</option>
              </select>
            </div>
          </div>

          <button type="submit" disabled={loading} className="search-button">
            {loading ? 'Searching...' : 'Search'}
          </button>
        </form>

        {error && (
          <div className="error-message">
            ❌ {error}
          </div>
        )}

        {results && (
          <div className="results-container">
            <div className="results-header">
              <h2>Search Results</h2>
              <p>
                Found {results.total} products 
                {results.total > 0 && (
                  <span> (showing {(results.page * results.size) + 1}-{Math.min((results.page + 1) * results.size, results.total)})</span>
                )}
              </p>
            </div>

            {results.items && results.items.length > 0 ? (
              <>
                <div className="results-list">
                  {results.items.map((item, index) => (
                    <div key={item.id || index} className="result-item">
                      <div className="result-header">
                        <h3 className="result-title">{item.title}</h3>
                        <div className="result-meta">
                          <span className="result-price">{formatPrice(item.price)}</span>
                          <span className="result-score">
                            Score: {(item.similarityScore * 100).toFixed(1)}%
                          </span>
                        </div>
                      </div>
                      
                      <div className="result-details">
                        <p className="result-category">
                          <strong>Category:</strong> {item.category}
                        </p>
                        <p className="result-snippet">
                          {getSnippet(item.description)}
                        </p>
                        {item.tags && item.tags.length > 0 && (
                          <div className="result-tags">
                            {item.tags.map((tag, tagIndex) => (
                              <span key={tagIndex} className="tag">{tag}</span>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>

                {results.total > results.size && (
                  <div className="pagination">
                    <button
                      onClick={() => handlePageChange(results.page - 1)}
                      disabled={results.page === 0}
                      className="pagination-button"
                    >
                      ← Previous
                    </button>
                    
                    <span className="pagination-info">
                      Page {results.page + 1} of {Math.ceil(results.total / results.size)}
                    </span>
                    
                    <button
                      onClick={() => handlePageChange(results.page + 1)}
                      disabled={!results.hasNext}
                      className="pagination-button"
                    >
                      Next →
                    </button>
                  </div>
                )}
              </>
            ) : (
              <div className="no-results">
                <p>No products found matching your criteria.</p>
                <p>Try adjusting your search terms or filters.</p>
              </div>
            )}
          </div>
        )}
      </main>

      <footer className="app-footer">
        <p>Powered by AI semantic search with Spring Boot & React</p>
      </footer>
    </div>
  );
}

export default App;
