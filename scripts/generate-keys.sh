#!/bin/bash

KEY_DIR=./keys
PRIVATE_KEY=$KEY_DIR/private_key.pem
PUBLIC_KEY=$KEY_DIR/public_key.pem

# Create key directory if it doesn't exist
mkdir -p "$KEY_DIR"

echo "🔐 Generating 2048-bit RSA private key..."
openssl genpkey -algorithm RSA -out "$PRIVATE_KEY" -pkeyopt rsa_keygen_bits:2048

echo "🔓 Extracting public key..."
openssl rsa -in "$PRIVATE_KEY" -pubout -out "$PUBLIC_KEY"

echo "✅ Keys generated:"
echo " - Private key: $PRIVATE_KEY"
echo " - Public key:  $PUBLIC_KEY"

