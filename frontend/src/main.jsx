import React from 'react';
import { createRoot } from 'react-dom/client';
import 'bootstrap/dist/css/bootstrap.min.css';
import './styles.css';
import App from './App.jsx';
import PrintableRoster from './PrintableRoster.jsx';
const printId = new URLSearchParams(window.location.search).get('printRoster');
createRoot(document.getElementById('root')).render(<React.StrictMode>{printId === null ? <App /> : <PrintableRoster id={printId} />}</React.StrictMode>);
