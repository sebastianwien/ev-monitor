package com.evmonitor.application.feedback;

import com.evmonitor.application.TripService;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.DataChannel;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.domain.feedback.DataFeedback;
import com.evmonitor.domain.feedback.DataFeedbackRepository;
import com.evmonitor.domain.feedback.FeedbackSubjectType;
import com.evmonitor.domain.feedback.FeedbackTag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "Korrekt erkannt?" für automatisch erfasste Ladevorgänge und Fahrten. Bewertet wird nur, was
 * der Nutzer sehen darf und was nicht von ihm selbst stammt - eine Bewertung eigener Eingaben
 * sagt nichts über die Erkennung.
 */
@Service
@RequiredArgsConstructor
public class DataFeedbackService {

    private static final Set<DataChannel> AUTOMATIC_LOG_CHANNELS = Set.of(DataChannel.SYNC, DataChannel.LIVE);
    private static final String USER_CREATED_TRIP = "USER_CREATED";

    private final DataFeedbackRepository feedbackRepository;
    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final EvTripRepository tripRepository;
    private final TripService tripService;

    @Transactional
    public DataFeedbackResponse submit(User user, FeedbackSubjectType type, UUID subjectId, DataFeedbackRequest request) {
        String dataSource = type == FeedbackSubjectType.LOG
                ? rateableLogSource(subjectId, user)
                : rateableTripSource(subjectId, user);
        for (FeedbackTag tag : request.tagsOrEmpty()) {
            if (!tag.appliesTo(type)) {
                throw new ValidationException("Tag " + tag + " passt nicht zu " + type);
            }
        }
        DataFeedback feedback = feedbackRepository.findBySubjectTypeAndSubjectId(type, subjectId)
                .orElseGet(() -> DataFeedback.create(user.getId(), type, subjectId, dataSource));
        feedback.rate(request.rating(), request.tagsOrEmpty(), blankToNull(request.comment()));
        return DataFeedbackResponse.from(feedbackRepository.save(feedback));
    }

    @Transactional
    public void clear(User user, FeedbackSubjectType type, UUID subjectId) {
        feedbackRepository.findBySubjectTypeAndSubjectId(type, subjectId)
                .filter(f -> f.getUserId().equals(user.getId()))
                .ifPresent(feedbackRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<DataFeedbackResponse> listForUser(UUID userId) {
        return feedbackRepository.findAllByUserId(userId).stream().map(DataFeedbackResponse::from).toList();
    }

    private String rateableLogSource(UUID logId, User user) {
        EvLog log = evLogRepository.findById(logId)
                .filter(l -> carRepository.findById(l.getCarId()).map(c -> c.isOwnedBy(user.getId())).orElse(false))
                .orElseThrow(() -> NotFoundException.forEntity("Log", logId));
        if (log.getDataSource() == null || !AUTOMATIC_LOG_CHANNELS.contains(log.getDataSource().channel())) {
            throw new ValidationException("Nur automatisch erfasste Ladevorgänge können bewertet werden");
        }
        return log.getDataSource().name();
    }

    private String rateableTripSource(UUID tripId, User user) {
        EvTrip trip = tripRepository.findById(tripId)
                .filter(t -> user.getId().equals(t.getUserId()))
                .filter(t -> t.getDeletedAt() == null)
                .filter(t -> tripService.userCanSeeTrip(t, user))
                .orElseThrow(() -> NotFoundException.forEntity("Trip", tripId));
        if (trip.getDataSource() == null || USER_CREATED_TRIP.equals(trip.getDataSource())) {
            throw new ValidationException("Nur erkannte Fahrten können bewertet werden");
        }
        return trip.getDataSource();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
