import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { ThemeProvider } from './theme';
import './index.css';

async function boot() {
  // 仅 mock 模式启用 MSW;生产构建中 MODE 为常量,这段会被整体裁掉
  if (import.meta.env.MODE === 'mock') {
    const { startMock } = await import('./mock/browser');
    await startMock();
  }
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <ThemeProvider>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </ThemeProvider>
    </React.StrictMode>,
  );
}
void boot();
