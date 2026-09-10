import { useState } from 'react'

const PRODUCTS = [
  { productId: 'P100', name: 'Wireless Mouse', stock: 25 },
  { productId: 'P200', name: 'Mechanical Keyboard', stock: 10 },
  { productId: 'P300', name: 'USB-C Hub', stock: 0 },
]

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

function App() {
  const [productId, setProductId] = useState('P100')
  const [quantity, setQuantity] = useState(1)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)

  async function submitOrder(event) {
    event.preventDefault()
    setLoading(true)
    setResult(null)

    try {
      const response = await fetch(`${API_URL}/api/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId, quantity: Number(quantity) }),
      })

      if (!response.ok) {
        throw new Error(`Request failed with HTTP ${response.status}`)
      }

      setResult(await response.json())
    } catch (error) {
      setResult({ status: 'ERROR', reason: error.message, inventory: null })
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="page">
      <section className="card">
        <p className="eyebrow">Modular Monolith Integration Lab</p>
        <h1>Place an Order</h1>
        <p className="subtitle">Order calls Inventory in-process, then persists the result to Supabase.</p>

        <form onSubmit={submitOrder}>
          <label>
            Product
            <select value={productId} onChange={(e) => setProductId(e.target.value)}>
              {PRODUCTS.map((product) => (
                <option key={product.productId} value={product.productId}>
                  {product.productId} — {product.name} (seed stock: {product.stock})
                </option>
              ))}
            </select>
          </label>

          <label>
            Quantity
            <input
              type="number"
              min="1"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              required
            />
          </label>

          <button type="submit" disabled={loading}>
            {loading ? 'Submitting…' : 'Submit Order'}
          </button>
        </form>

        {result && (
          <div className={`result ${result.status.toLowerCase()}`}>
            <div className="result-status">{result.status}</div>
            <p>{result.reason}</p>
            {result.inventory && (
              <div className="inventory">
                <strong>{result.inventory.name}</strong>
                <span>{result.inventory.productId}</span>
                <span>Remaining stock: {result.inventory.stock}</span>
              </div>
            )}
          </div>
        )}
      </section>
    </main>
  )
}

export default App
