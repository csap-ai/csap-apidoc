const http = require('http');

http.get('http://localhost:9528', (res) => {
  let data = '';
  res.on('data', (chunk) => { data += chunk; });
  res.on('end', () => {
    console.log('HTML Length:', data.length);
    console.log('Contains root div:', data.includes('<div id="root">'));
    console.log('Contains main.tsx:', data.includes('main.tsx'));
    
    // 检查CSS
    http.get('http://localhost:9528/src/styles/index.scss', (cssRes) => {
      console.log('CSS Status:', cssRes.statusCode);
    }).on('error', (err) => {
      console.log('CSS Error:', err.message);
    });
  });
}).on('error', (err) => {
  console.log('Error:', err.message);
});
