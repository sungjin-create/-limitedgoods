import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));

function argument(name, fallback) {
    const index = process.argv.indexOf(`--${name}`);
    return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

function positiveInteger(name, fallback) {
    const value = Number(argument(name, fallback));
    if (!Number.isInteger(value) || value <= 0) {
        throw new Error(`${name} must be a positive integer: ${value}`);
    }
    return value;
}

const baseUrl = argument('base-url', 'http://localhost:18080');
const userCount = positiveInteger('user-count', '5000');
const concurrency = positiveInteger('concurrency', '100');
const userPrefix = argument('user-prefix', 'loadtest');
const password = argument('password', 'Test1234!');
const outputArgument = argument(
    'output',
    path.join(scriptDirectory, 'access-tokens.json'),
);
const outputPath = path.isAbsolute(outputArgument)
    ? outputArgument
    : path.resolve(process.cwd(), outputArgument);

const tokens = new Array(userCount);
let nextIndex = 1;
let completed = 0;

async function login(index) {
    const response = await fetch(`${baseUrl}/api/user/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            email: `${userPrefix}${index}@loadtest.local`,
            password,
        }),
        signal: AbortSignal.timeout(30_000),
    });

    const body = await response.json().catch(() => null);
    const token = body?.data?.accessToken;

    if (!response.ok || body?.success === false || typeof token !== 'string' || !token) {
        throw new Error(
            `login failed for user ${index}: HTTP ${response.status}, code=${body?.code || 'unknown'}`,
        );
    }

    tokens[index - 1] = token;
    completed += 1;

    if (completed % 250 === 0 || completed === userCount) {
        process.stdout.write(`prepared ${completed}/${userCount} tokens\n`);
    }
}

async function worker() {
    while (true) {
        const index = nextIndex;
        nextIndex += 1;
        if (index > userCount) return;
        await login(index);
    }
}

await Promise.all(
    Array.from({ length: Math.min(concurrency, userCount) }, () => worker()),
);

if (new Set(tokens).size !== userCount) {
    throw new Error('duplicate access tokens were generated');
}

await mkdir(path.dirname(outputPath), { recursive: true });
await writeFile(outputPath, JSON.stringify(tokens), { encoding: 'utf8', mode: 0o600 });

process.stdout.write(`saved ${userCount} tokens to ${outputPath}\n`);
