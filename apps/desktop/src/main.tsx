import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

function App() {
  return <main>问答桌面应用脚手架</main>;
}

createRoot(document.getElementById('root')!).render(
  <StrictMode><App /></StrictMode>
);
