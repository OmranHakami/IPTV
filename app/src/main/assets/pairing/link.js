const form = document.querySelector('#pair-form');
const codeInput = document.querySelector('#code');
const status = document.querySelector('#status');
const submit = document.querySelector('#submit');

const bytesToBase64 = (bytes) => {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
};
const base64ToBytes = (value) => Uint8Array.from(atob(value), character => character.charCodeAt(0));
const bytesToBigInt = (bytes) => BigInt(`0x${Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')}`);
const bigIntToBytes = (value, length) => {
  const hex = value.toString(16).padStart(length * 2, '0');
  return Uint8Array.from({ length }, (_, index) => parseInt(hex.slice(index * 2, index * 2 + 2), 16));
};
const modPow = (base, exponent, modulus) => {
  let result = 1n;
  let value = base % modulus;
  let power = exponent;
  while (power > 0n) {
    if (power & 1n) result = (result * value) % modulus;
    value = (value * value) % modulus;
    power >>= 1n;
  }
  return result;
};
const randomNonZero = (length) => {
  const result = new Uint8Array(length);
  for (let index = 0; index < length; index += 1) {
    let value = 0;
    while (value === 0) value = crypto.getRandomValues(new Uint8Array(1))[0];
    result[index] = value;
  }
  return result;
};
const encryptChunk = (chunk, modulus, exponent, keyBytes) => {
  const paddingLength = keyBytes - chunk.length - 3;
  if (paddingLength < 8) throw new Error('Credential block is too large.');
  const encoded = new Uint8Array(keyBytes);
  encoded[1] = 2;
  encoded.set(randomNonZero(paddingLength), 2);
  encoded[2 + paddingLength] = 0;
  encoded.set(chunk, 3 + paddingLength);
  return bytesToBase64(bigIntToBytes(modPow(bytesToBigInt(encoded), exponent, modulus), keyBytes));
};
const normalizedCode = () => codeInput.value.toUpperCase().replace(/[^A-Z0-9]/g, '');

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  submit.disabled = true;
  status.textContent = 'Checking your TV code…';
  try {
    const code = normalizedCode();
    const lookup = await fetch(`/session?code=${encodeURIComponent(code)}`, { cache: 'no-store' });
    if (!lookup.ok) throw new Error('That code is invalid or has expired. Create a new code on your TV.');
    const session = await lookup.json();
    const modulusBytes = base64ToBytes(session.modulus);
    const modulus = bytesToBigInt(modulusBytes);
    const exponent = BigInt(session.exponent);
    const payload = new TextEncoder().encode(JSON.stringify({
      displayName: document.querySelector('#displayName').value || 'Living Room TV',
      serverUrl: document.querySelector('#serverUrl').value.trim(),
      username: document.querySelector('#username').value.trim(),
      password: document.querySelector('#password').value,
    }));
    const maxChunk = modulusBytes.length - 11;
    const chunks = [];
    for (let offset = 0; offset < payload.length; offset += maxChunk) {
      chunks.push(encryptChunk(payload.slice(offset, offset + maxChunk), modulus, exponent, modulusBytes.length));
    }
    status.textContent = 'Sending encrypted details directly to your TV…';
    const sent = await fetch('/submit', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ code, chunks }),
    });
    if (!sent.ok) throw new Error('The local pairing session expired. Create a new code on your TV.');
    form.reset();
    document.querySelector('#form-card').classList.add('hidden');
    document.querySelector('#success-card').classList.remove('hidden');
  } catch (error) {
    status.textContent = error instanceof Error ? error.message : 'Pairing failed. Try again.';
    submit.disabled = false;
  }
});
