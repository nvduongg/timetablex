import axios from '../utils/axiosConfig';

export const getLecturers = (facultyId = null, options = {}) => {
  const params = { ...(facultyId && { facultyId }), ...options };
  return axios.get('/lecturers', { params });
};
export const createLecturer = (data) => axios.post('/lecturers', data);
export const updateLecturer = (id, data) => axios.put(`/lecturers/${id}`, data);
export const deleteLecturer = (id) => axios.delete(`/lecturers/${id}`);
export const downloadTemplate = () => axios.get('/lecturers/template', { responseType: 'blob' });
export const importLecturer = (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return axios.post('/lecturers/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
};

export const updateCompetency = (id, courseIds) => axios.put(`/lecturers/${id}/competency`, { courseIds });