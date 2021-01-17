package io.github.danthe1st.customslashcommands;

import lombok.*;

import java.io.Serializable;

@Data
@RequiredArgsConstructor
@NoArgsConstructor
public class CustomCommand implements Serializable {
    private long id=-1;
    @NonNull
    private String name;
    @NonNull
    private String description;
    @NonNull
    private String response;

    @Override
    public String toString() {
        return name+" ("+description+"): "+response;
    }
}
