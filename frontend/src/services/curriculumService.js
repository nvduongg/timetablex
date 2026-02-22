import axios from '../utils/axiosConfig';

export const getCurriculums = () => axios.get('/curriculums');
export const createCurriculum = (data) => axios.post('/curriculums', data);
export const deleteCurriculum = (id) => axios.delete(`/curriculums/${id}`);
export const removeDetail = (detailId) => axios.delete(`/curriculums/details/${detailId}`);
export const addDetail = (curriculumId, courseId, semesterIndex) =>
    axios.post(`/curriculums/${curriculumId}/details`, { courseId, semesterIndex });

// Download mẫu
export const downloadRoadmapTemplate = () => axios.get('/curriculums/roadmap-template', { responseType: 'blob' });

// Import lộ trình vào 1 Curriculum ID cụ thể
export const importRoadmap = (id, file) => {
    const formData = new FormData();
    formData.append('file', file);
    return axios.post(`/curriculums/${id}/import`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
};

export const getCohorts = () => axios.get('/curriculums/cohorts');