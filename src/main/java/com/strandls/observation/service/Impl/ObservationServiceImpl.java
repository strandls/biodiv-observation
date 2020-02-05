/**
 * 
 */
package com.strandls.observation.service.Impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.pac4j.core.profile.CommonProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.strandls.authentication_utility.util.AuthUtil;
import com.strandls.esmodule.controllers.EsServicesApi;
import com.strandls.esmodule.pojo.ObservationInfo;
import com.strandls.esmodule.pojo.ObservationNearBy;
import com.strandls.naksha.controller.LayerServiceApi;
import com.strandls.naksha.pojo.ObservationLocationInfo;
import com.strandls.observation.dao.ObservationDAO;
import com.strandls.observation.dao.RecommendationVoteDao;
import com.strandls.observation.pojo.AllRecoSugguestions;
import com.strandls.observation.pojo.Observation;
import com.strandls.observation.pojo.ObservationCreate;
import com.strandls.observation.pojo.ObservationUpdateData;
import com.strandls.observation.pojo.ObservationUserPermission;
import com.strandls.observation.pojo.RecoCreate;
import com.strandls.observation.pojo.RecoIbp;
import com.strandls.observation.pojo.ShowData;
import com.strandls.observation.service.ObservationService;
import com.strandls.observation.util.ObservationInputException;
import com.strandls.resource.controllers.ResourceServicesApi;
import com.strandls.resource.pojo.ObservationResourceUser;
import com.strandls.resource.pojo.Resource;
import com.strandls.taxonomy.controllers.TaxonomyServicesApi;
import com.strandls.taxonomy.pojo.SpeciesGroup;
import com.strandls.taxonomy.pojo.TaxonTree;
import com.strandls.traits.controller.TraitsServiceApi;
import com.strandls.traits.pojo.FactValuePair;
import com.strandls.traits.pojo.TraitsValue;
import com.strandls.traits.pojo.TraitsValuePair;
import com.strandls.user.controller.UserServiceApi;
import com.strandls.user.pojo.Follow;
import com.strandls.user.pojo.SpeciesPermission;
import com.strandls.user.pojo.UserGroupMemberRole;
import com.strandls.user.pojo.UserIbp;
import com.strandls.user.pojo.UserPermissions;
import com.strandls.userGroup.controller.UserGroupSerivceApi;
import com.strandls.userGroup.pojo.Featured;
import com.strandls.userGroup.pojo.FeaturedCreate;
import com.strandls.userGroup.pojo.ObservationLatLon;
import com.strandls.userGroup.pojo.UserGroupIbp;
import com.strandls.utility.controller.UtilityServiceApi;
import com.strandls.utility.pojo.Flag;
import com.strandls.utility.pojo.FlagIbp;
import com.strandls.utility.pojo.Language;
import com.strandls.utility.pojo.Tags;
import com.strandls.utility.pojo.TagsMapping;

import net.minidev.json.JSONArray;

/**
 * @author Abhishek Rudra
 *
 */
public class ObservationServiceImpl implements ObservationService {

	private static final Logger logger = LoggerFactory.getLogger(ObservationServiceImpl.class);

	@Inject
	private LogActivities logActivity;

	@Inject
	private ObservationDAO observationDao;

	@Inject
	private TraitsServiceApi traitService;

	@Inject
	private TaxonomyServicesApi taxonomyService;

	@Inject
	private ResourceServicesApi resourceService;

	@Inject
	private UserGroupSerivceApi userGroupService;

	@Inject
	private LayerServiceApi layerService;

	@Inject
	private EsServicesApi esService;

	@Inject
	private RecommendationServiceImpl recoService;

	@Inject
	private UtilityServiceApi utilityServices;

	@Inject
	private UserServiceApi userService;

	@Inject
	private ObservationMapperHelper observationHelper;

	@Inject
	private RecommendationVoteDao recoVoteDao;

	@Override
	public ShowData findById(Long id) {

		InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("config.properties");

		Properties properties = new Properties();
		try {
			properties.load(in);
		} catch (IOException e) {
			logger.error(e.getMessage());
		}

		List<FactValuePair> facts;
		List<ObservationResourceUser> observationResource;
		List<UserGroupIbp> userGroups;
		ObservationLocationInfo layerInfo;
		ObservationInfo esLayerInfo = null;
		RecoIbp reco = null;
		List<Flag> flag = new ArrayList<Flag>();
		List<Tags> tags;
		List<Featured> fetaured;
		UserIbp userInfo;
		List<RecoIbp> allRecoVotes = null;
		List<AllRecoSugguestions> recoaggregated = null;
		Observation observation = observationDao.findById(id);
		if (observation != null && observation.getIsDeleted() != true) {
			try {
				facts = traitService.getFacts("species.participation.Observation", id.toString());
				observationResource = resourceService.getImageResource(id.toString());
				userGroups = userGroupService.getObservationUserGroup(id.toString());
				layerInfo = layerService.getLayerInfo(String.valueOf(observation.getLatitude()),
						String.valueOf(observation.getLongitude()));
				if (observation.getFlagCount() > 0)
					flag = utilityServices.getFlagByObjectType("observation", id.toString());
				tags = utilityServices.getTags("observation", id.toString());
				userInfo = userService.getUserIbp(observation.getAuthorId().toString());
				fetaured = userGroupService.getAllFeatured("species.participation.Observation", id.toString());
				if (observation.getMaxVotedRecoId() != null) {
					reco = recoService.fetchRecoName(id, observation.getMaxVotedRecoId());
					esLayerInfo = esService.getObservationInfo("observation", "observation",
							observation.getMaxVotedRecoId().toString());
					allRecoVotes = recoService.allRecoVote(id);
					recoaggregated = aggregateAllRecoSuggestions(allRecoVotes);
				}

				if (observation.getGeoPrivacy()) {
					Map<String, Double> latlon = observationHelper.getRandomLatLong(observation.getLatitude(),
							observation.getLongitude());
					observation.setLatitude(latlon.get("lat"));
					observation.setLongitude(latlon.get("lon"));
				}

				List<ObservationNearBy> observationNearBy = esService.getNearByObservation("observation", "observation",
						observation.getLatitude().toString(), observation.getLongitude().toString());
				ShowData data = new ShowData(observation, facts, observationResource, userGroups, layerInfo,
						esLayerInfo, reco, flag, tags, fetaured, userInfo, recoaggregated, observationNearBy);

				observation.setVisitCount(observation.getVisitCount() + 1);
				observationDao.update(observation);
				return data;
			} catch (Exception e) {
				logger.error(e.getMessage());
			}
		}
		return null;
	}

	@Override
	public List<AllRecoSugguestions> aggregateAllRecoSuggestions(List<RecoIbp> allRecoVote) {
		List<AllRecoSugguestions> result = new ArrayList<AllRecoSugguestions>();
		for (RecoIbp reco : allRecoVote) {
			int updated = 0;
			for (AllRecoSugguestions recoSuggestions : result) {
				if (reco.getTaxonId() != null && recoSuggestions.getTaxonId() != null) {
					if (reco.getTaxonId().equals(recoSuggestions.getTaxonId())) {
						result.remove(recoSuggestions);
						if (recoSuggestions.getCommonName().trim().length() == 0
								&& reco.getCommonName().trim().length() != 0)
							recoSuggestions.setCommonName(reco.getCommonName());
						if (recoSuggestions.getScientificName().trim().length() == 0
								&& reco.getScientificName().trim().length() != 0)
							recoSuggestions.setScientificName(reco.getScientificName());
						if (recoSuggestions.getSpeciesId() == null && reco.getSpeciesId() != null)
							recoSuggestions.setSpeciesId(reco.getSpeciesId());
						List<UserIbp> userList = recoSuggestions.getUserList();
						userList.add(reco.getUserIbp());
						recoSuggestions.setUserList(userList);
						result.add(recoSuggestions);
						updated = 1;
						break;
					}
				} else if (reco.getTaxonId() == null && recoSuggestions.getTaxonId() == null) {
					if (recoSuggestions.getCommonName().equals(reco.getCommonName())
							|| recoSuggestions.getScientificName().equals(reco.getScientificName())) {
						result.remove(recoSuggestions);
						if (recoSuggestions.getCommonName().trim().length() == 0
								&& reco.getCommonName().trim().length() != 0)
							recoSuggestions.setCommonName(reco.getCommonName());
						if (recoSuggestions.getScientificName().trim().length() == 0
								&& reco.getScientificName().trim().length() != 0)
							recoSuggestions.setScientificName(reco.getScientificName());
						if (recoSuggestions.getSpeciesId() == null && reco.getSpeciesId() != null)
							recoSuggestions.setSpeciesId(reco.getSpeciesId());
						List<UserIbp> userList = recoSuggestions.getUserList();
						userList.add(reco.getUserIbp());
						recoSuggestions.setUserList(userList);
						result.add(recoSuggestions);
						updated = 1;
						break;

					}
				}

			}
			if (updated == 0) {
				List<UserIbp> userList = new ArrayList<UserIbp>();
				userList.add(reco.getUserIbp());
				AllRecoSugguestions suggestion = new AllRecoSugguestions(reco.getCommonName(), reco.getScientificName(),
						reco.getTaxonId(), reco.getSpeciesId(), userList);
				result.add(suggestion);
			}
		}

		return result;

	}

	@Override
	public ShowData createObservation(HttpServletRequest request, ObservationCreate observationData) {

		try {
			CommonProfile profile = AuthUtil.getProfileFromRequest(request);
			Long userId = Long.parseLong(profile.getId());
			Long maxVotedReco = null;
			Observation observation = observationHelper.createObservationMapping(userId, observationData);
			observation = observationDao.save(observation);

			logActivity.LogActivity(null, observation.getId(), observation.getId(), "observation", null,
					"Observation created");

			if (!(observationData.getHelpIdentify())) {
				RecoCreate recoCreate = observationHelper.createRecoMapping(observationData.getRecoData());
				maxVotedReco = recoService.createRecoVote(userId, observation.getId(),
						observationData.getRecoData().getScientificNameTaxonId(), recoCreate);
			}

			List<Resource> resources = observationHelper.createResourceMapping(userId, observationData.getResources());
			resourceService.createResource("OBSERVATION", String.valueOf(observation.getId()), resources);

			traitService.createFacts("species.participation.Observation", String.valueOf(observation.getId()),
					observationData.getFacts());

			userGroupService.createObservationUserGroupMapping(String.valueOf(observation.getId()),
					observationData.getUserGroupId());
			if (!(observationData.getTags().isEmpty())) {
				TagsMapping tagsMapping = new TagsMapping();
				tagsMapping.setObjectId(observation.getId());
				tagsMapping.setTags(observationData.getTags());
				utilityServices.createTags("observation", tagsMapping);

			}

//			update observaiton object

			observation.setMaxVotedRecoId(maxVotedReco);
			Integer noOfImages = 0;
			Integer noOfAudio = 0;
			Integer noOfVideo = 0;

			Long reprImage = resources.get(0).getId();
			int rating = 0;
			for (Resource res : resources) {
				if (res.getType().equals("AUDIO"))
					noOfAudio++;
				else if (res.getType().equals("IMAGE")) {
					noOfImages++;
					if (res.getRating() != null && res.getRating() > rating) {
						reprImage = res.getId();
						rating = res.getRating();
					}
				} else if (res.getType().equals("VIDEO"))
					noOfVideo++;

			}
			observation.setNoOfAudio(noOfAudio);
			observation.setNoOfImages(noOfImages);
			observation.setNoOfVideos(noOfVideo);
			observation.setReprImageId(reprImage);
			observationDao.update(observation);
//			----------------POST CREATE ACTIONS------------

//			----------------GEO PRIVACY CHECK-------------
			List<Observation> observationList = new ArrayList<Observation>();
			observationList.add(observation);
			updateGeoPrivacy(observationList);

//			---------------USER GROUP FILTER RULE----------
			ObservationLatLon latlon = new ObservationLatLon();
			latlon.setLatitude(observation.getLatitude());
			latlon.setLongitude(observation.getLongitude());
			latlon.setObservationId(observation.getId());
			userGroupService.getFilterRule(latlon);
			return findById(observation.getId());

		} catch (Exception e) {
			logger.error(e.getMessage());
		}

		return null;

	}

	@Override
	public Long updateSGroup(Long observationId, Long sGroupId) {
		Observation observation = observationDao.findById(observationId);
		Long previousGroupId = observation.getGroupId();
		observation.setGroupId(sGroupId);
		observation.setLastRevised(new Date());
		observation = observationDao.update(observation);
		List<SpeciesGroup> SpeciesGroupList = getAllSpeciesGroup();
		String previousGroupName = "";
		String newGroupName = "";
		for (SpeciesGroup speciesGroup : SpeciesGroupList) {
			if (speciesGroup.getId().equals(previousGroupId))
				previousGroupName = speciesGroup.getName();
			if (speciesGroup.getId().equals(sGroupId))
				newGroupName = speciesGroup.getName();
		}
		String description = previousGroupName + " to " + newGroupName;
		logActivity.LogActivity(description, observationId, observationId, "observation", observationId,
				"Observation species group updated");
		return observation.getGroupId();
	}

	@Override
	public Long updateMaxVotedReco(Long observationId, Long maxVotedReco) {
		Observation observation = observationDao.findById(observationId);
		if (observation.getMaxVotedRecoId() != maxVotedReco) {
			observation.setMaxVotedRecoId(maxVotedReco);
			observation.setLastRevised(new Date());
			observation.setNoOfIdentifications(recoVoteDao.findRecoVoteCount(observationId));
			observationDao.update(observation);
			List<Observation> observationList = new ArrayList<Observation>();
			observationList.add(observation);
			updateGeoPrivacy(observationList);
			return maxVotedReco;
		}
		return observation.getMaxVotedRecoId();
	}

	@Override
	public List<Tags> updateTags(TagsMapping tagsMapping) {
		List<Tags> result = null;
		try {
			result = utilityServices.updateTags("observation", tagsMapping);
			Observation observation = observationDao.findById(tagsMapping.getObjectId());
			observation.setLastRevised(new Date());
			observationDao.update(observation);
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return result;
	}

	@Override
	public List<FactValuePair> updateTraits(String observationId, String traitId, List<Long> valueList) {

		List<FactValuePair> facts = null;
		try {
			facts = traitService.updateTraits("species.participation.Observation", observationId, traitId, valueList);
			Observation observation = observationDao.findById(Long.parseLong(observationId));
			observation.setLastRevised(new Date());
			observationDao.update(observation);
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return facts;
	}

	@Override
	public List<UserGroupIbp> updateUserGroup(String observationId, List<Long> userGroupList) {

		List<UserGroupIbp> result = null;
		try {
			result = userGroupService.updateUserGroupMapping(observationId, userGroupList);
			Observation observation = observationDao.findById(Long.parseLong(observationId));
			observation.setLastRevised(new Date());
			observationDao.update(observation);
		} catch (Exception e) {
			logger.error(e.getMessage());
		}

		return result;
	}

	@Override
	public List<SpeciesGroup> getAllSpeciesGroup() {

		List<SpeciesGroup> result = null;
		try {
			result = taxonomyService.getAllSpeciesGroup();
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return result;
	}

	@Override
	public List<Language> getLanguages(Boolean isDirty) {

		List<Language> result = null;
		try {
			result = utilityServices.getAllLanguages(isDirty);

		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return result;
	}

	@Override
	public List<Featured> createFeatured(FeaturedCreate featuredCreate) {
		List<Featured> result = null;

		try {
			result = userGroupService.createFeatured(featuredCreate);
			Observation observation = observationDao.findById(featuredCreate.getObjectId());
			observation.setLastRevised(new Date());
			observationDao.update(observation);
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return result;
	}

	@Override
	public List<Featured> unFeatured(String observaitonId, List<Long> userGroupList) {
		List<Featured> result = null;
		try {
			result = userGroupService.unFeatured("observation", observaitonId, userGroupList);
			Observation observation = observationDao.findById(Long.parseLong(observaitonId));
			observation.setLastRevised(new Date());
			observationDao.update(observation);
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return result;
	}

	@Override
	public List<TraitsValue> getTraitsValue(String traitId) {
		List<TraitsValue> result = null;
		try {
			result = traitService.getTraitsValue(traitId);
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return result;
	}

	@Override
	public List<TraitsValuePair> getTraitList(String speciesId) {

		List<TraitsValuePair> result = null;
		try {
			result = traitService.getTraitList(speciesId);
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return result;
	}

	@Override
	public ObservationUserPermission getUserPermissions(CommonProfile profile, String observationId, Long userId,
			String taxonList) {
		try {
			List<UserGroupIbp> associatedUserGroup = userGroupService.getObservationUserGroup(observationId);
			List<Long> validateAllowed = new ArrayList<Long>();
			List<UserGroupIbp> allowedUserGroup = null;
			List<Long> userGroupFeatureRole = new ArrayList<Long>();
			UserPermissions userPermission = userService.getAllUserPermission("observation", observationId);

			JSONArray userRole = (JSONArray) profile.getAttribute("roles");
			if (userRole.contains("ROLE_ADMIN")) {
				for (String s : taxonList.split(",")) {
					validateAllowed.add(Long.parseLong(s));
				}
				allowedUserGroup = userGroupService.getAllUserGroup();
				for (UserGroupIbp ug : allowedUserGroup) {
					userGroupFeatureRole.add(ug.getId());
				}

			} else {
				if (taxonList.trim().length() != 0) {
					List<TaxonTree> taxonTree = taxonomyService.getTaxonTree(taxonList);
					validateAllowed = ValidatePermission(taxonTree, userPermission.getAllowedTaxonList());

				}

				List<Long> userGroupMember = new ArrayList<Long>();
				for (UserGroupMemberRole userMemberRole : userPermission.getUserMemberRole()) {
					userGroupMember.add(userMemberRole.getUserGroupId());
				}
				String s = userGroupMember.toString();
				allowedUserGroup = userGroupService.getUserGroupList(s.substring(1, s.length() - 1));

				for (UserGroupMemberRole userFeatureRole : userPermission.getUserFeatureRole()) {
					userGroupFeatureRole.add(userFeatureRole.getUserGroupId());
				}
			}

			List<UserGroupIbp> featureableGroup = new ArrayList<UserGroupIbp>();
			for (UserGroupIbp userGroup : associatedUserGroup) {
				if (userGroupFeatureRole.contains(userGroup.getId()))
					featureableGroup.add(userGroup);

			}

			ObservationUserPermission permission = new ObservationUserPermission(validateAllowed, allowedUserGroup,
					featureableGroup, userPermission.getFollowing());
			return permission;

		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return null;
	}

	private List<Long> ValidatePermission(List<TaxonTree> taxonTrees, List<SpeciesPermission> allowedTaxons) {

		List<Long> validateAllowable = new ArrayList<Long>();

		for (SpeciesPermission allowtaxon : allowedTaxons) {
			for (TaxonTree taxonTree : taxonTrees) {
				if (taxonTree.getTaxonList().contains(allowtaxon.getTaxonConceptId())) {
					if (!(validateAllowable.contains(taxonTree.getTaxonId())))
						validateAllowable.add(taxonTree.getTaxonId());
				}
			}
		}
		return validateAllowable;

	}

	@Override
	public List<Tags> getTagsSugguestions(String phrase) {
		try {
			List<Tags> result = utilityServices.getTagsAutoComplete(phrase);
			return result;
		} catch (Exception e) {
			logger.error(e.getMessage());
		}

		return null;
	}

	@Override
	public List<UserGroupIbp> getUsersGroupList() {
		try {
			UserPermissions userPermission = userService.getUserGroupPermissions();
			List<Long> userGroupMember = new ArrayList<Long>();
			for (UserGroupMemberRole userMemberRole : userPermission.getUserMemberRole()) {
				userGroupMember.add(userMemberRole.getUserGroupId());
			}
			String s = userGroupMember.toString();
			List<UserGroupIbp> allowedUserGroup = userGroupService.getUserGroupList(s.substring(1, s.length() - 1));

			return allowedUserGroup;
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return null;
	}

	@Override
	public String removeObservation(CommonProfile profile, Long userId, Long observationId) {

		JSONArray userRole = (JSONArray) profile.getAttribute("roles");
		Observation observation = observationDao.findById(observationId);
		if (observation.getAuthorId().equals(userId) || userRole.contains("ROLE_ADMIN")) {
			observation.setIsDeleted(true);
			observationDao.update(observation);
			return "Observation Deleted Succesfully";
		}

		return null;
	}

	@Override
	public List<Flag> createFlag(Long observationId, FlagIbp flagIbp) {
		try {
			List<Flag> flagList = utilityServices.createFlag("observation", observationId.toString(), flagIbp);
			int flagCount = 0;
			if (flagList != null)
				flagCount = flagList.size();

			Observation observation = observationDao.findById(observationId);
			observation.setLastRevised(new Date());
			observation.setFlagCount(flagCount);
			observationDao.update(observation);
			return flagList;
		} catch (Exception e) {
			logger.error(e.getMessage());
		}

		return null;
	}

	@Override
	public List<Flag> unFlag(Long observationId, Flag flag) {
		try {
			List<Flag> result = utilityServices.unFlag("observation", observationId.toString(), flag);
			int flagCount = 0;
			if (result != null)
				flagCount = result.size();

			Observation observation = observationDao.findById(observationId);
			observation.setLastRevised(new Date());
			observation.setFlagCount(flagCount);
			observationDao.update(observation);
			return result;
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return null;
	}

	@Override
	public Follow followRequest(Long observationId) {
		try {
			Follow result = userService.updateFollow("observation", observationId.toString());
			return result;
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return null;
	}

	@Override
	public Follow unFollowRequest(Long observationId) {
		try {
			Follow result = userService.unfollow("observation", observationId.toString());
			return result;
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return null;
	}

	@Override
	public Long getObservationAuthor(Long observationId) {
		Observation observation = observationDao.findById(observationId);
		Long authorId = observation.getAuthorId();
		return authorId;
	}

	@Override
	public ShowData editObservaitonCore(CommonProfile profile, Long observationId,
			ObservationUpdateData observationUpdate) {

		try {
			JSONArray userRoles = (JSONArray) profile.getAttribute("roles");
			Long userId = Long.parseLong(profile.getId());
			Observation observation = observationDao.findById(observationId);
			if (observation.getAuthorId().equals(userId) || userRoles.contains("ROLE_ADMIN")) {
//				location data
				observation.setPlaceName(observationUpdate.getObservedAt());
				observation.setReverseGeocodedName(observationUpdate.getReverseGeocoded());
				observation.setLocationScale(observationUpdate.getLocationScale());
				observation.setLatitude(observationUpdate.getLatitude());
				observation.setLongitude(observationUpdate.getLongitude());
				observation.setGeoPrivacy(observationUpdate.getHidePreciseLocation());
//				notes
				observation.setNotes(observationUpdate.getNotes());
//				date data
				observation.setFromDate(observationUpdate.getObservedOn());
				observation.setToDate(observationUpdate.getObservedOn());
				observation.setDateAccuracy(observationUpdate.getDateAccuracy());
				observation.setLastRevised(new Date());
//				resource data

				List<Resource> resources = observationHelper.createResourceMapping(userId,
						observationUpdate.getResources());
				resourceService.updateResources("OBSERVATION", String.valueOf(observation.getId()), resources);

//				calculate reprImageof observation

				Integer noOfImages = 0;
				Integer noOfAudio = 0;
				Integer noOfVideo = 0;

				Long reprImage = resources.get(0).getId();
				int rating = 0;
				for (Resource res : resources) {
					if (res.getType().equals("AUDIO"))
						noOfAudio++;
					else if (res.getType().equals("IMAGE")) {
						noOfImages++;
						if (res.getRating() != null && res.getRating() > rating) {
							reprImage = res.getId();
							rating = res.getRating();
						}
					} else if (res.getType().equals("VIDEO"))
						noOfVideo++;

				}
				observation.setNoOfAudio(noOfAudio);
				observation.setNoOfImages(noOfImages);
				observation.setNoOfVideos(noOfVideo);
				observation.setReprImageId(reprImage);
				observationDao.update(observation);

//				---------GEO PRIVACY CHECK------------
				List<Observation> observationList = new ArrayList<Observation>();
				observationList.add(observation);
				updateGeoPrivacy(observationList);

				logActivity.LogActivity(null, observationId, observationId, "observation", observationId,
						"Observation updated");
				return findById(observationId);
			} else {
				try {
					throw new ObservationInputException("USER NOT ALLOWED TO UPDATE THE OBSERVATION");
				} catch (Exception e) {
					logger.error(e.getMessage());
				}
			}
			return null;
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return null;

	}

	@Override
	public ObservationUpdateData getObservationEditPageData(CommonProfile profile, Long observationId) {
		ObservationUpdateData editData = new ObservationUpdateData();
		try {
			JSONArray userRoles = (JSONArray) profile.getAttribute("roles");
			Long userId = Long.parseLong(profile.getId());
			Observation observation = observationDao.findById(observationId);
			if (observation.getAuthorId().equals(userId) || userRoles.contains("ROLE_ADMIN")) {
//				notes data
				editData.setNotes(observation.getNotes());
//				Date data
				editData.setDateAccuracy(observation.getDateAccuracy());
				editData.setObservedOn(observation.getFromDate());
//				location data
				editData.setObservedAt(observation.getPlaceName());
				editData.setReverseGeocoded(observation.getReverseGeocodedName());
				editData.setLocationScale(observation.getLocationScale());
				editData.setLatitude(observation.getLatitude());
				editData.setLongitude(observation.getLongitude());
				editData.setHidePreciseLocation(observation.getGeoPrivacy());

//				resources Data
				List<ObservationResourceUser> resourceData = resourceService.getImageResource(observationId.toString());
				editData.setResources(observationHelper.createEditResourceMapping(resourceData));

			} else {
				throw new ObservationInputException("USER NOT ALLOWED TO EDIT THE PAGE");
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return editData;
	}

	@Override
	public void applyFilterObservation(String userGroupIds) {
		try {

			Boolean hasNext = true;
			int totalObservation = 0;
			int startPoint = 0;
			while (hasNext) {
				List<Observation> observationList = observationDao.fetchInBatch(startPoint);
				if (observationList.size() != 50000)
					hasNext = false;
				totalObservation = totalObservation + observationList.size();
				startPoint = totalObservation + 1;
				List<ObservationLatLon> latlonList = new ArrayList<ObservationLatLon>();
				ObservationLatLon latlon = new ObservationLatLon();
				for (Observation observation : observationList) {
					latlon.setObservationId(observation.getId());
					latlon.setLatitude(observation.getLatitude());
					latlon.setLongitude(observation.getLongitude());
					latlonList.add(latlon);
				}
				userGroupService.bulkFilterRule(userGroupIds, latlonList);
			}

			System.out.println("Filter Rule Process Completed");

		} catch (Exception e) {
			logger.error(e.getMessage());
		}

	}

	@Override
	public void applyGeoPrivacyObservaiton() {
		try {
			Boolean hasNext = true;
			int totalObservation = 0;
			int startPoint = 0;
			while (hasNext) {

				System.out.println("--------START----------");
				System.out.println("START POINT : " + startPoint);
				System.out.println("total Observation till this point : " + totalObservation);
				System.out.println("---------END-----------");
				List<Observation> observationList = observationDao.fetchInBatch(startPoint);
				if (observationList.size() != 20000)
					hasNext = false;
				totalObservation = totalObservation + observationList.size();
				startPoint = totalObservation + 1;
				updateGeoPrivacy(observationList);
			}

		} catch (Exception e) {
			logger.error(e.getMessage());
		}

	}

	private void updateGeoPrivacy(List<Observation> observationList) {

		try {

			InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("config.properties");

			Properties properties = new Properties();
			try {
				properties.load(in);
			} catch (IOException e) {
				e.printStackTrace();
			}
			String geoPrivacyTraitsValue = properties.getProperty("geoPrivacyValues");
			in.close();

			List<Long> geoPrivateTaxonId = traitService.getTaxonListByValueId(geoPrivacyTraitsValue);

			for (Observation observation : observationList) {
				System.out.println("--------START---------");
				System.out.println("Observation Id : " + observation.getId());
				System.out.println("---------END----------");

				if (observation.getGeoPrivacy() == false && observation.getMaxVotedRecoId() != null) {
					Long taxonId = recoService.fetchTaxonId(observation.getMaxVotedRecoId());
					if (taxonId != null) {

						if (geoPrivateTaxonId.contains(taxonId)) {
							System.out.println("---------BEGIN----------");
							System.out.println("Observation Id : " + observation.getId());
							observation.setGeoPrivacy(true);
							observationDao.update(observation);
							System.out.println("----------END------------");
						}

					}
				}
			}

		} catch (Exception e) {
			logger.error(e.getMessage());
		}

	}

}
