import axios from '../utils/axiosConfig';

export const login = (username, password) =>
  axios.post('/auth/login', { username, password });

export const createFacultyAccount = (facultyId) =>
  axios.post('/auth/create-faculty-account', { facultyId });

export const getUsers = () =>
  axios.get('/admin/users');

export const toggleUserActive = (id) =>
  axios.put(`/admin/users/${id}/toggle-active`);

export const resetUserPassword = (id) =>
  axios.post(`/admin/users/${id}/reset-password`);

