package keifer.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * What every product was worth at the start of each range the dashboard offers.
 *
 * The dates come back alongside the prices because they are not always the dates that were
 * asked for. The history starts the night the server began keeping it, so a ninety day window
 * opened in its first month measures from the oldest day on record instead, and the screen says
 * "since the 12th" rather than claiming ninety days it does not have.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceBaselines {

    /** Window day count -> the day actually measured from. */
    private Map<String, LocalDate> dates;

    /** Product id -> window day count -> what one copy was worth that day. */
    private Map<String, Map<String, Double>> prices;

    /** The dates as plain ISO text, which is how every other date reaches the browser. */
    public Map<String, String> datesAsText() {

        Map<String, String> text = new HashMap<>();
        dates.forEach((window, date) -> text.put(window, date.toString()));

        return text;
    }

}
