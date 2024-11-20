// index.js

// Simulamos un error en 5 segundos
setTimeout(() => {
    // Forzamos un error
    const error = new Error("¡Este es un error simulado!");
    console.error(error);  // El error será registrado en la consola
    throw error;  // Lanza el error
}, 5000);
