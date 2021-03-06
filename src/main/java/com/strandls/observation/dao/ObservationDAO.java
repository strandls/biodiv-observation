/**
 * 
 */
package com.strandls.observation.dao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.ProjectionList;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.strandls.observation.pojo.Observation;
import com.strandls.observation.util.AbstractDAO;

/**
 * @author Abhishek Rudra
 *
 */
public class ObservationDAO extends AbstractDAO<Observation, Long> {

	private static final Logger logger = LoggerFactory.getLogger(ObservationDAO.class);

	@Inject
	protected ObservationDAO(SessionFactory sessionFactory) {
		super(sessionFactory);
	}

	@Override
	public Observation findById(Long id) {
		Session session = sessionFactory.openSession();
		Observation entity = null;
		try {
			entity = session.get(Observation.class, id);
		} catch (Exception e) {
			logger.info(e.getMessage());
			logger.error(e.toString());
		} finally {
			session.close();
		}
		return entity;
	}

	@SuppressWarnings("unchecked")
	public List<Observation> fetchInBatch(int startPoint) {
		List<Observation> result = null;
		Session session = sessionFactory.openSession();
		String qry = "from Observation where isDeleted = false and geoPrivacy = false and maxVotedRecoId is not NULL  order by id";
		try {
			Query<Observation> query = session.createQuery(qry);
			query.setMaxResults(20000);
			query.setFirstResult(startPoint);
			result = query.getResultList();

		} catch (Exception e) {
			logger.error(e.getMessage());
		} finally {
			session.close();
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public List<Observation> fecthByListOfIds(List<Long> observationList) {
		String qry = "from Observation where isDeleted = false and id IN :ids";
		Session session = sessionFactory.openSession();
		List<Observation> result = null;
		try {
			Query<Observation> query = session.createQuery(qry);
			query.setParameter("ids", observationList);
			result = query.getResultList();
		} catch (Exception e) {
			logger.error(e.getMessage());
		}

		return result;
	}

	@SuppressWarnings("unchecked")
	public List<Observation> fetchInBatchRecoCalculate(int startPoint) {
		List<Observation> result = null;
		Session session = sessionFactory.openSession();
		String qry = "from Observation where isDeleted = false and noOfIdentifications = 0 and maxVotedRecoId is not NULL order by id";
		try {
			Query<Observation> query = session.createQuery(qry);
			query.setMaxResults(5000);
			query.setFirstResult(startPoint);
			result = query.getResultList();

		} catch (Exception e) {
			logger.error(e.getMessage());
		} finally {
			session.close();
		}
		return result;
	}

	@SuppressWarnings({ "unchecked", "deprecation" })
	public List<Object[]> getValuesOfColumnsBasedOnFilter(List<String> projectedColumns, Map<String, Object> filterOn) {
		Session session = sessionFactory.openSession();
		Criteria criteria = session.createCriteria(Observation.class);
		ProjectionList projectionList = Projections.projectionList();
		for (String projectedColumn : projectedColumns) {
			projectionList.add(Projections.property(projectedColumn));
		}
		criteria.add(Restrictions.allEq(filterOn));
		criteria.setProjection(projectionList);
		List<Object[]> queryData = criteria.list();
		session.close();
		return queryData;
	}

	@SuppressWarnings("unchecked")
	public Map<Long, Long> getObservationUploadCount(Long authorId, Long sGroup, Boolean hasMedia, Long offset) {
		Session session = sessionFactory.openSession();
		String qry = "select max_voted_reco_id, count(id) from observation "
				+ "where is_deleted = false and  author_id = " + authorId + " and max_voted_reco_id is not NULL ";
		if (sGroup != null)
			qry = qry + " and group_id = " + sGroup;
		if (hasMedia)
			qry = qry + " and (no_of_audio > 0 or no_of_videos > 0 or no_of_images > 0 ) ";

		qry = qry + "group by max_voted_reco_id order by count(id) desc limit 10 offset " + offset;
		
		
		String qry1 = "select count(id) from observation "
				+ "where is_deleted = false and  author_id = " + authorId + " and max_voted_reco_id is not NULL ";
		if (sGroup != null)
			qry1 = qry1 + " and s_group = " + sGroup;
		if (hasMedia)
			qry1 = qry1 + " and (no_of_audio > 0 or no_of_videos > 0 or no_of_images > 0 ) ";

		Map<Long, Long> maxVotedRecoFreq = new LinkedHashMap<Long, Long>();

		List<Object[]> objectList = null;

		try {

			Query<Object[]> query = session.createNativeQuery(qry);

			objectList = query.getResultList();

			for (Object object[] : objectList) {
				maxVotedRecoFreq.put(Long.parseLong(object[0].toString()), Long.parseLong(object[1].toString()));
			}
			
			Query<Object> query2 = session.createNativeQuery(qry1);
			Object obj = query2.getSingleResult();
			maxVotedRecoFreq.put(null, Long.parseLong(obj.toString()));
			
		} catch (Exception e) {
			logger.error(e.getMessage());
		} finally {
			session.close();
		}
		return maxVotedRecoFreq;
	}

}
