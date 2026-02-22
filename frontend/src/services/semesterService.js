import axios from '../utils/axiosConfig';

export const getSemesters = () => axios.get('/semesters');
export const createSemester = (data) => axios.post('/semesters', data);
export const updateSemester = (id, data) => axios.put(`/semesters/${id}`, data);
export const deleteSemester = (id) => axios.delete(`/semesters/${id}`);
export const downloadTemplate = () => axios.get('/semesters/template', { responseType: 'blob' });
export const importSemester = (file) => {
  const fd = new FormData();
  fd.append('file', file);
  return axios.post('/semesters/import', fd, { headers: { 'Content-Type': 'multipart/form-data' } });
};