#!/bin/bash
GITHUB_TOKEN="github_pat_11AE4RMZQ0JpuLEXLf0iaw_eEvZbA6qjkQ4uhZ1FE7K8I4YtZEXH7DUIYKGrB0V2fzGNJGCQ36FtE7"
REPO="pcfat/istar-app"

# Function to encrypt and add secret
add_secret() {
    local secret_name=$1
    local secret_value=$2
    
    # Get public key
    KEY_DATA=$(curl -s -H "Authorization: token $GITHUB_TOKEN" \
        "https://api.github.com/repos/$REPO/actions/secrets/public-key")
    
    KEY_ID=$(echo $KEY_DATA | jq -r '.key_id')
    PUBLIC_KEY=$(echo $KEY_DATA | jq -r '.key')
    
    # Encrypt secret using Python with nacl
    python3 << PYTHON
import base64
import json
from nacl import encoding, public

def encrypt_secret(public_key: str, secret_value: str) -> str:
    public_key_bytes = base64.b64decode(public_key)
    public_key_obj = public.PublicKey(public_key_bytes)
    sealed_box = public.SealedBox(public_key_obj)
    encrypted = sealed_box.encrypt(secret_value.encode("utf-8"))
    return base64.b64encode(encrypted).decode("utf-8")

print(encrypt_secret("$PUBLIC_KEY", """$secret_value"""))
PYTHON
}

echo "Installing dependencies..."
pip3 install -q PyNaCl

echo "Adding KEYCHAIN_PASSWORD..."
ENCRYPTED=$(add_secret "KEYCHAIN_PASSWORD" "actions")
curl -s -X PUT -H "Authorization: token $GITHUB_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"encrypted_value\":\"$ENCRYPTED\",\"key_id\":\"$KEY_ID\"}" \
    "https://api.github.com/repos/$REPO/actions/secrets/KEYCHAIN_PASSWORD"

echo "Done!"
