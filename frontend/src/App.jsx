import { useState, useEffect } from 'react'

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
const LOW_STOCK_THRESHOLD = 5

export default function App() {
  const [inventory, setInventory] = useState([])
  const [orders, setOrders] = useState([])
  const [notifications, setNotifications] = useState([])
  
  // Cart state
  const [cart, setCart] = useState([])
  const [selectedProductId, setSelectedProductId] = useState('')
  const [selectedQuantity, setSelectedQuantity] = useState(1)
  
  // Order submission state
  const [submitting, setSubmitting] = useState(false)
  const [lastResult, setLastResult] = useState(null)
  const [cancellingOrderId, setCancellingOrderId] = useState(null)
  const [actionError, setActionError] = useState(null)

  // Fetch all dashboard data
  async function refreshAll() {
    try {
      const [invRes, ordRes, notifRes] = await Promise.all([
        fetch(`${API_URL}/api/inventory`),
        fetch(`${API_URL}/api/orders`),
        fetch(`${API_URL}/api/notifications`),
      ])

      if (invRes.ok) {
        const invData = await invRes.json()
        setInventory(invData)
        if (!selectedProductId && invData.length > 0) {
          setSelectedProductId(invData[0].productId)
        }
      }
      if (ordRes.ok) {
        setOrders(await ordRes.json())
      }
      if (notifRes.ok) {
        setNotifications(await notifRes.json())
      }
    } catch (err) {
      console.error('Failed to fetch dashboard data:', err)
    }
  }

  useEffect(() => {
    refreshAll()
  }, [])

  // Cart operations
  function addToCart(productId, qty) {
    setActionError(null)
    const quantityToAdd = Number(qty)
    if (quantityToAdd <= 0) return

    setCart((prev) => {
      const existing = prev.find((item) => item.productId === productId)
      if (existing) {
        return prev.map((item) =>
          item.productId === productId
            ? { ...item, quantity: item.quantity + quantityToAdd }
            : item
        )
      }
      return [...prev, { productId, quantity: quantityToAdd }]
    })
  }

  function updateCartQuantity(productId, newQty) {
    const qty = Number(newQty)
    if (qty <= 0) {
      removeFromCart(productId)
    } else {
      setCart((prev) =>
        prev.map((item) =>
          item.productId === productId ? { ...item, quantity: qty } : item
        )
      )
    }
  }

  function removeFromCart(productId) {
    setCart((prev) => prev.filter((item) => item.productId !== productId))
  }

  function clearCart() {
    setCart([])
    setLastResult(null)
  }

  // Submit multi-item order
  async function submitOrder(e) {
    if (e) e.preventDefault()
    if (cart.length === 0) return

    setSubmitting(true)
    setLastResult(null)
    setActionError(null)

    try {
      const response = await fetch(`${API_URL}/api/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          items: cart.map((c) => ({ productId: c.productId, quantity: c.quantity })),
        }),
      })

      const data = await response.json()
      setLastResult(data)
      if (data.status === 'CONFIRMED') {
        setCart([])
      }
      await refreshAll()
    } catch (err) {
      setActionError('Order submission failed: ' + err.message)
    } finally {
      setSubmitting(false)
    }
  }

  // Cancel order
  async function cancelOrder(orderId) {
    setCancellingOrderId(orderId)
    setActionError(null)

    try {
      const response = await fetch(`${API_URL}/api/orders/${orderId}/cancel`, {
        method: 'POST',
      })

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(errorData.message || `Failed with status ${response.status}`)
      }

      await refreshAll()
    } catch (err) {
      setActionError(`Cannot cancel Order #${orderId}: ` + err.message)
    } finally {
      setCancellingOrderId(null)
    }
  }

  const getProductName = (pid) => {
    const item = inventory.find((i) => i.productId === pid)
    return item ? item.name : pid
  }

  const totalCartUnits = cart.reduce((sum, item) => sum + item.quantity, 0)

  return (
    <div className="dashboard-layout">
      {/* Header */}
      <header className="navbar">
        <div className="navbar-brand">
          <div className="logo-badge">LAB 2</div>
          <div>
            <h1>Modular Monolith Dashboard</h1>
            <p className="subtitle">
              Order, Inventory & Notification In-Process Monolith with Domain Events
            </p>
          </div>
        </div>
        <div className="navbar-actions">
          <span className="live-indicator">
            <span className="pulsing-dot"></span> In-Monolith Pub/Sub Active
          </span>
          <button id="btn-refresh" className="btn-secondary" onClick={refreshAll} title="Refresh Dashboard">
            🔄 Refresh
          </button>
        </div>
      </header>

      {actionError && (
        <div className="banner error-banner">
          ⚠️ {actionError}
        </div>
      )}

      {/* 2-Column Grid */}
      <main className="grid-container">
        {/* Left Column: Inventory & Cart */}
        <section className="column">
          {/* Inventory Panel */}
          <div className="card">
            <div className="card-header">
              <div>
                <h2>Live Inventory</h2>
                <p className="card-desc">Current stock levels (auto-refreshed on order/cancel)</p>
              </div>
              <span className="threshold-tag">Threshold: &lt; {LOW_STOCK_THRESHOLD}</span>
            </div>

            <table className="data-table" id="inventory-table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th>ID</th>
                  <th>Stock</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {inventory.map((item) => {
                  const isOutOfStock = item.stock === 0
                  const isLowStock = item.stock > 0 && item.stock < LOW_STOCK_THRESHOLD
                  const rowClass = isOutOfStock
                    ? 'row-out-of-stock'
                    : isLowStock
                    ? 'row-low-stock'
                    : ''

                  return (
                    <tr key={item.productId} className={rowClass}>
                      <td><strong>{item.name}</strong></td>
                      <td><code>{item.productId}</code></td>
                      <td className="stock-cell">
                        <span className="stock-number">{item.stock}</span>
                      </td>
                      <td>
                        {isOutOfStock && <span className="badge badge-danger">OUT OF STOCK</span>}
                        {isLowStock && <span className="badge badge-warning">LOW STOCK (&lt;5)</span>}
                        {!isOutOfStock && !isLowStock && (
                          <span className="badge badge-success">IN STOCK</span>
                        )}
                      </td>
                      <td>
                        <button
                          id={`btn-add-quick-${item.productId}`}
                          className="btn-small"
                          onClick={() => addToCart(item.productId, 1)}
                        >
                          + Add
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {/* Cart & Multi-Item Order Form */}
          <div className="card">
            <div className="card-header">
              <div>
                <h2>Multi-Item Cart</h2>
                <p className="card-desc">All-or-nothing transactional reservation</p>
              </div>
              {cart.length > 0 && (
                <button className="btn-text" onClick={clearCart}>
                  Clear Cart
                </button>
              )}
            </div>

            <form
              className="add-item-form"
              onSubmit={(e) => {
                e.preventDefault()
                if (selectedProductId) {
                  addToCart(selectedProductId, selectedQuantity)
                }
              }}
            >
              <div className="form-group flex-1">
                <label>Select Product</label>
                <select
                  id="select-product"
                  value={selectedProductId}
                  onChange={(e) => setSelectedProductId(e.target.value)}
                >
                  {inventory.map((item) => (
                    <option key={item.productId} value={item.productId}>
                      {item.productId} — {item.name} (stock: {item.stock})
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group quantity-group">
                <label>Qty</label>
                <input
                  id="input-quantity"
                  type="number"
                  min="1"
                  value={selectedQuantity}
                  onChange={(e) => setSelectedQuantity(Number(e.target.value))}
                  required
                />
              </div>

              <button id="btn-add-to-cart" type="submit" className="btn-secondary add-btn">
                Add to Cart
              </button>
            </form>

            {/* Cart Items List */}
            <div className="cart-content">
              {cart.length === 0 ? (
                <div className="empty-state">
                  Cart is empty. Select products above to build a multi-item order.
                </div>
              ) : (
                <div className="cart-list">
                  {cart.map((item) => {
                    const inv = inventory.find((i) => i.productId === item.productId)
                    const stock = inv ? inv.stock : '?'
                    const exceeds = inv && item.quantity > inv.stock

                    return (
                      <div key={item.productId} className={`cart-item ${exceeds ? 'cart-item-warning' : ''}`}>
                        <div className="cart-item-info">
                          <strong>{getProductName(item.productId)}</strong>
                          <span className="cart-item-id">
                            <code>{item.productId}</code> · Avail: {stock}
                            {exceeds && <span className="warning-text"> (Exceeds stock!)</span>}
                          </span>
                        </div>
                        <div className="cart-item-controls">
                          <button
                            type="button"
                            className="btn-stepper"
                            onClick={() => updateCartQuantity(item.productId, item.quantity - 1)}
                          >
                            -
                          </button>
                          <span className="cart-quantity">{item.quantity}</span>
                          <button
                            type="button"
                            className="btn-stepper"
                            onClick={() => updateCartQuantity(item.productId, item.quantity + 1)}
                          >
                            +
                          </button>
                          <button
                            type="button"
                            className="btn-remove"
                            onClick={() => removeFromCart(item.productId)}
                            title="Remove item"
                          >
                            ✕
                          </button>
                        </div>
                      </div>
                    )
                  })}

                  <div className="cart-summary">
                    <span>Total items: <strong>{totalCartUnits}</strong> across {cart.length} line item(s)</span>
                  </div>

                  <button
                    id="btn-submit-order"
                    type="button"
                    className="btn-primary full-width"
                    disabled={submitting}
                    onClick={submitOrder}
                  >
                    {submitting ? 'Submitting Order…' : `Submit Order (${totalCartUnits} units)`}
                  </button>
                  <p className="guarantee-note">
                    🔒 <strong>All-or-Nothing Rule:</strong> If any item exceeds available stock, the whole order is rejected and NO stock is reserved.
                  </p>
                </div>
              )}
            </div>

            {/* Order Result Display */}
            {lastResult && (
              <div
                id="order-result-card"
                className={`result-card ${lastResult.status.toLowerCase()}`}
              >
                <div className="result-header">
                  <span className={`badge badge-large ${lastResult.status === 'CONFIRMED' ? 'badge-success' : 'badge-danger'}`}>
                    {lastResult.status}
                  </span>
                  {lastResult.orderId && <span className="order-tag">Order #{lastResult.orderId}</span>}
                </div>
                <p className="result-reason">{lastResult.reason}</p>

                {lastResult.items && lastResult.items.length > 0 && (
                  <div className="result-outcomes">
                    <h4>Line Item Outcomes:</h4>
                    <ul>
                      {lastResult.items.map((item, idx) => (
                        <li key={idx} className="outcome-item">
                          <span>{getProductName(item.productId)} (<code>{item.productId}</code>) × {item.quantity}</span>
                          <span className={`outcome-badge outcome-${item.outcome.toLowerCase()}`}>
                            {item.outcome}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}
          </div>
        </section>

        {/* Right Column: Order History & Activity Feed */}
        <section className="column">
          {/* Order History */}
          <div className="card">
            <div className="card-header">
              <div>
                <h2>Order History</h2>
                <p className="card-desc">Live orders persisted in Supabase</p>
              </div>
              <span className="count-pill">{orders.length} orders</span>
            </div>

            {orders.length === 0 ? (
              <div className="empty-state">No orders placed yet.</div>
            ) : (
              <div className="orders-list" id="orders-list">
                {orders.map((order) => {
                  const isConfirmed = order.status === 'CONFIRMED'
                  const isCancelled = order.status === 'CANCELLED'
                  const isRejected = order.status === 'REJECTED'

                  return (
                    <div key={order.orderId} className={`order-card order-${order.status.toLowerCase()}`}>
                      <div className="order-header">
                        <div className="order-id-block">
                          <strong>Order #{order.orderId}</strong>
                          <span className={`badge ${
                            isConfirmed ? 'badge-success' : isCancelled ? 'badge-neutral' : 'badge-danger'
                          }`}>
                            {order.status}
                          </span>
                        </div>
                        {order.createdAt && (
                          <span className="order-time">
                            {new Date(order.createdAt).toLocaleTimeString()}
                          </span>
                        )}
                      </div>

                      <div className="order-body">
                        {order.reason && <p className="order-reason">{order.reason}</p>}
                        <div className="order-items-pills">
                          {order.items && order.items.map((it, idx) => (
                            <span key={idx} className="item-pill">
                              {it.quantity}× {getProductName(it.productId)} (<code>{it.productId}</code>)
                            </span>
                          ))}
                        </div>
                      </div>

                      {isConfirmed && (
                        <div className="order-actions">
                          <button
                            id={`btn-cancel-order-${order.orderId}`}
                            className="btn-cancel"
                            disabled={cancellingOrderId === order.orderId}
                            onClick={() => cancelOrder(order.orderId)}
                          >
                            {cancellingOrderId === order.orderId ? 'Cancelling…' : 'Cancel & Restock'}
                          </button>
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          {/* Activity Feed / Notifications */}
          <div className="card">
            <div className="card-header">
              <div>
                <h2>Notification Feed</h2>
                <p className="card-desc">Domain events published across modules via Spring Pub/Sub</p>
              </div>
              <span className="count-pill">{notifications.length} events</span>
            </div>

            {notifications.length === 0 ? (
              <div className="empty-state">No domain events recorded yet.</div>
            ) : (
              <div className="activity-feed" id="notifications-feed">
                {notifications.map((notif) => {
                  const isLowStock = notif.message.toLowerCase().includes('low stock')
                  const isRejected = notif.message.toLowerCase().includes('rejected')
                  const isConfirmed = notif.message.toLowerCase().includes('confirmed')

                  return (
                    <div
                      key={notif.notificationId}
                      className={`activity-card ${
                        isLowStock ? 'activity-lowstock' : isRejected ? 'activity-rejected' : 'activity-confirmed'
                      }`}
                    >
                      <div className="activity-icon">
                        {isLowStock ? '⚠️' : isRejected ? '❌' : '✅'}
                      </div>
                      <div className="activity-content">
                        <p className="activity-message">{notif.message}</p>
                        {notif.createdAt && (
                          <span className="activity-time">
                            {new Date(notif.createdAt).toLocaleTimeString()} · #{notif.notificationId}
                          </span>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </section>
      </main>
    </div>
  )
}
