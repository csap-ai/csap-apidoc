#!/bin/bash

BASE_URL="http://localhost:8080"

echo "========================================="
echo "  Testing CSAP API Doc Example APIs"
echo "========================================="
echo ""

# Test if server is running
echo "🔍 Checking if server is running..."
if ! curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/api/users" | grep -q "200"; then
    echo "❌ Server is not running. Please start the application first:"
    echo "   ./run.sh"
    exit 1
fi
echo "✅ Server is running"
echo ""

# Test 1: Get user list
echo "📋 Test 1: Get user list"
curl -s -X GET "${BASE_URL}/api/users?page=1&pageSize=10" \
  -H "Accept: application/json" | jq '.'
echo ""

# Test 2: Get user by ID
echo "📋 Test 2: Get user by ID"
curl -s -X GET "${BASE_URL}/api/users/1001" \
  -H "Accept: application/json" | jq '.'
echo ""

# Test 3: Create user
echo "📋 Test 3: Create user"
curl -s -X POST "${BASE_URL}/api/users" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test_user",
    "email": "test@example.com",
    "phone": "13800138000",
    "age": 25
  }' | jq '.'
echo ""

# Test 4: Get product list
echo "📋 Test 4: Get product list"
curl -s -X GET "${BASE_URL}/api/products?page=1&pageSize=10" \
  -H "Accept: application/json" | jq '.'
echo ""

# Test 5: Create product
echo "📋 Test 5: Create product"
curl -s -X POST "${BASE_URL}/api/products" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Product",
    "description": "This is a test product",
    "price": 99.99,
    "stock": 50,
    "categoryId": 1,
    "onSale": true
  }' | jq '.'
echo ""

# Test 6: Get API documentation
echo "📋 Test 6: Get API documentation metadata"
curl -s -X GET "${BASE_URL}/api/csap/doc" \
  -H "Accept: application/json" | jq '.controllers | length' | xargs echo "Total controllers:"
echo ""

echo "========================================="
echo "✅ All tests completed!"
echo ""
echo "📚 View full API documentation:"
echo "   ${BASE_URL}/csap-api.html"
echo "========================================="

