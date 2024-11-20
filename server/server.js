const express = require('express');
const { Server } = require('socket.io');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');
const cors = require('cors');

const app = express();
const port = 3000;

const server = app.listen(port, () => {
    console.log(`Servidor escuchando en el puerto ${port}`);
});

const io = new Server(server);

const scriptsData = {};

// Middleware para logging
app.use(cors());


// Inicializar los estados de los scripts como "stopped"
function initializeScripts() {
    const scriptsDir = path.join(__dirname, 'scripts');
    fs.readdir(scriptsDir, (err, files) => {
        if (err) {
            console.error('Error al listar scripts:', err);
            return;
        }
        const scripts = files.filter(file => fs.statSync(path.join(scriptsDir, file)).isDirectory());
        scripts.forEach(scriptName => {
            if (!scriptsData[scriptName]) {
                scriptsData[scriptName] = {
                    status: 'stopped',
                    logs: { stdout: [], stderr: [] },
                    process: null
                };
            }
        });
    });
}

// Llama a esta función al iniciar el servidor
initializeScripts();

// Ruta para listar scripts disponibles
app.get('/scripts', (req, res) => {
    const scriptsDir = path.join(__dirname, 'scripts');
    fs.readdir(scriptsDir, (err, files) => {
        if (err) {
            console.error('Error al leer la carpeta de scripts:', err);
            return res.status(500).json({ error: 'Error al listar scripts' });
        }
        const scripts = files.filter(file => fs.statSync(path.join(scriptsDir, file)).isDirectory());
        res.json(scripts);
    });
});

// Ruta para obtener el estado de un script
app.get('/scripts/:scriptName/status', (req, res) => {
    const { scriptName } = req.params;
    const scriptData = scriptsData[scriptName];
    if (!scriptData) {
        return res.status(200).json({ status: 'stopped' }); 
    }
    res.json({
        status: scriptData.status,
        stdoutLogs: scriptData.logs.stdout,
        stderrLogs: scriptData.logs.stderr,
    });
});

// Emitir actualizaciones de scripts periódicamente
function emitScripts() {
    const scriptsDir = path.join(__dirname, 'scripts');
    fs.readdir(scriptsDir, (err, files) => {
        if (err) {
            console.error('Error al listar scripts:', err);
            return;
        }
        const scripts = files.filter(file => fs.statSync(path.join(scriptsDir, file)).isDirectory());
        io.emit('scripts_update', scripts);
    });
}
setInterval(emitScripts, 10000);

// Ruta para iniciar o detener un script
app.post('/scripts/:scriptName/:action', (req, res) => {
    const { scriptName, action } = req.params;
    const scriptPath = path.join(__dirname, 'scripts', scriptName, 'index.js');

    if (!fs.existsSync(scriptPath)) {
        return res.status(404).json({ error: 'Script no encontrado' });
    }

    if (action === 'start') {
        if (scriptsData[scriptName]?.process) {
            return res.status(400).json({ error: 'Script ya está en ejecución' });
        }

        const scriptProcess = spawn('node', [scriptPath]);
        scriptsData[scriptName] = {
            process: scriptProcess,
            logs: { stdout: [], stderr: [] },
            status: 'running',
        };

        scriptProcess.stdout.on('data', (data) => {
            const log = data.toString();
            scriptsData[scriptName].logs.stdout.push(log);
            io.emit('log_stdout', { scriptName, log });
        });
        
        scriptProcess.stderr.on('data', (data) => {
            const errorLog = data.toString();
            scriptsData[scriptName].logs.stderr.push(errorLog);
            io.emit('log_stderr', { scriptName, errorLog });
        });

        scriptProcess.on('exit', (code) => {
            scriptsData[scriptName].status = 'stopped';
            delete scriptsData[scriptName].process;
            io.emit('status', { scriptName, status: 'stopped', code });
        });

        io.emit('status', { scriptName, status: 'running' });
        res.json({ message: 'Script iniciado', scriptName });
    } else if (action === 'stop') {
        const scriptProcess = scriptsData[scriptName]?.process;
        if (!scriptProcess) {
            return res.status(400).json({ error: 'Script no está en ejecución' });
        }
        scriptProcess.kill();
        scriptsData[scriptName].status = 'stopped';
        delete scriptsData[scriptName].process;
        io.emit('status', { scriptName, status: 'stopped' });
        res.json({ message: 'Script detenido', scriptName });
    } else {
        res.status(400).json({ error: 'Acción no válida' });
    }
});

// Escuchar conexiones socket
io.on('connection', (socket) => {
    console.log('Cliente conectado');

    
    const initialStates = Object.keys(scriptsData).map(scriptName => ({
        scriptName,
        status: scriptsData[scriptName].status,
    }));
    socket.emit('initial_status', initialStates);

    socket.on('disconnect', () => {
        console.log('Cliente desconectado');
    });
});
