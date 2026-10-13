// Local S3 protocol fixture for isolated acceptance; not a production storage service.
import http from 'node:http';
import { createHash } from 'node:crypto';

const objects = new Map();
const port = Number(process.argv[2] || 9900);
http.createServer(async (request, response) => {
  const path = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
  if (path === '/__fixture/stats') {
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify({ objects: objects.size }));
    return;
  }
  if (request.method === 'PUT') {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    let body = Buffer.concat(chunks);
    if (request.headers['content-encoding']?.includes('aws-chunked')) {
      const decoded = [];
      let offset = 0;
      while (offset < body.length) {
        const end = body.indexOf('\r\n', offset);
        const size = parseInt(body.subarray(offset, end).toString().split(';')[0], 16);
        if (!size) break;
        decoded.push(body.subarray(end + 2, end + 2 + size));
        offset = end + 2 + size + 2;
      }
      body = Buffer.concat(decoded);
    }
    objects.set(path, { body, type: request.headers['content-type'] || 'application/octet-stream' });
    response.setHeader('ETag', `"${createHash('md5').update(body).digest('hex')}"`);
    response.end();
    return;
  }
  const object = objects.get(path);
  if (request.method === 'HEAD' && path.split('/').filter(Boolean).length <= 1) {
    response.end();
  } else if (object && ['GET', 'HEAD'].includes(request.method)) {
    response.setHeader('Content-Type', object.type);
    response.setHeader('Content-Length', object.body.length);
    response.setHeader('Last-Modified', new Date().toUTCString());
    response.setHeader('ETag', `"${createHash('md5').update(object.body).digest('hex')}"`);
    response.end(request.method === 'HEAD' ? undefined : object.body);
  } else {
    response.writeHead(404, { 'Content-Type': 'application/xml' });
    response.end('<Error><Code>NoSuchKey</Code></Error>');
  }
}).listen(port, '127.0.0.1', () => console.log(`Isolated S3 fixture listening on ${port}`));
