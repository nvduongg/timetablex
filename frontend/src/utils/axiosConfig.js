import axios from 'axios';

// Khi truy cập từ máy khác trong mạng (http://IP:5173), API phải trỏ về cùng IP:8080
const apiBaseURL = typeof window !== 'undefined'
    ? `${window.location.protocol}//${window.location.hostname}:8080/api`
    : 'http://localhost:8080/api';

const instance = axios.create({
    baseURL: apiBaseURL,
    timeout: 10000,
});

instance.interceptors.request.use((config) => {
    const token = localStorage.getItem('auth_token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

instance.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response && (error.response.status === 401 || error.response.status === 403)) {
            localStorage.removeItem('auth_token');
            localStorage.removeItem('auth_user');
            if (typeof window !== 'undefined' && window.location.pathname !== '/') {
                window.location.href = '/';
            }
        }
        return Promise.reject(error);
    }
);

export default instance;