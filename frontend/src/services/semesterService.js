import axios from '../utils/axiosConfig';

export const getSemesters = () => axios.get('/semesters');
export const createSemester = (data) => axios.post('/semesters', data);
export const updateSemester = (id, data) => axios.put(`/semesters/${id}`, data);
export const deleteSemester = (id) => axios.delete(`/semesters/${id}`);