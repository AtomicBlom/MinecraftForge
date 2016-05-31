package net.minecraftforge.common.plon;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.animation.Event;
import net.minecraftforge.common.animation.ITimeValue;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.animation.AnimationStateMachineBase;
import net.minecraftforge.common.model.animation.Clips;
import net.minecraftforge.common.model.animation.IAnimationStateMachine;
import net.minecraftforge.common.model.animation.IClip;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Created by rainwarrior on 5/28/16.
 */

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import static net.minecraftforge.common.plon.AST.*;
public enum Glue implements IResourceManagerReloadListener
{
    INSTANCE;

    /*public static ISExp getLoadOp()
    {
        return GlueOp.Load;
    }

    public static ISExp getModelClip()
    {
        return GlueOp.ModelClip;
    }

    public static ISExp getTriggerPositive()
    {
        return GlueOp.TriggerPositive;
    }*/

    public static Interpreter getInterpreter()
    {
        return in;
    }

    public static ISExp getUserOp(final Function<? super String, Optional<? extends ITimeValue>> userParameters)
    {
        return new ICallableExp()
        {
            @Override
            public ISExp getType()
            {
                return User.asbType;
            }

            @Override
            public ISExp apply(IList args)
            {
                if (length(args) != 1)
                {
                    throw new IllegalArgumentException("user needs 1 argument, got: " + args);
                }
                Cons c2 = (Cons) args;
                if (c2.car instanceof StringAtom)
                {
                    String parameterName = ((StringAtom) c2.car).value;
                    Optional<? extends ITimeValue> parameter = userParameters.apply(parameterName);
                    if (parameter == null || !parameter.isPresent())
                    {
                        throw new IllegalArgumentException("Unknown user parameter: " + parameterName);
                    }
                    return new User(parameterName, parameter.get());
                }
                throw new IllegalArgumentException("user needs string argument, got: " + args);
            }

            @Override
            public String toString()
            {
                return "&user";
            }
        };
    }

    private static IResourceManager manager;
    private static final ResourceLocation rootLibraryLocation = new ResourceLocation("forge", "root_library_unstable");
    private static Map rootLibrary;
    private static final Interpreter in = new Interpreter()
    {
        @Override
        protected ISExp read(String location)
        {
            try
            {
                return Glue.read(new ResourceLocation(location));
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException("Couldn't load " + location, e);
            }
        }
    };

    private static class User implements ICallableExp
    {
        private static final ISExp type = absType(ImmutableList.of(PrimTypes.Float.type), PrimTypes.Float.type);
        private static final ISExp asbType = absType(ImmutableList.of(PrimTypes.String.type), type);
        private final String name;
        private final ITimeValue parameter;

        User(String name, ITimeValue parameter)
        {
            this.name = name;
            this.parameter = parameter;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            User user = (User) o;
            return Objects.equal(parameter, user.parameter);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(parameter);
        }

        @Override
        public String toString()
        {
            return "&user[" + name + "]";
        }

        public ISExp apply(IList args)
        {
            if (length(args) != 1)
            {
                throw new IllegalArgumentException("User parameter \"" + name + "\" needs 1 argument, got " + args);
            }
            Cons cons = (Cons) args;
            if (cons.car instanceof FloatAtom)
            {
                return makeFloat(parameter.apply(((FloatAtom) cons.car).value));
            }
            throw new IllegalArgumentException("User parameter \"" + name + "\" needs float argument, got " + cons.car);
        }

        @Override
        public ISExp getType()
        {
            return type;
        }
    }

    /*private static class ClipValue implements IAtom
    {
        private final IClip clip;
        private final float time;

        ClipValue(IClip clip, float time)
        {
            this.clip = clip;
            this.time = time;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClipValue other = (ClipValue) o;
            return Objects.equal(clip, other.clip) && time == other.time;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(clip, time);
        }

        @Override
        public String toString()
        {
            return "&clip[" + clip + ", " + time + "]";
        }
    }*/

    private static ResourceLocation getPlonLocation(ResourceLocation loc)
    {
        return new ResourceLocation(loc.getResourceDomain(), "plon/" + loc.getResourcePath() + ".json");
    }

    /*private enum GlueOp implements ICallableExp
    {
        ModelClip("model_clip")
        {
            @Override
            public ISExp apply(IList args)
            {
                if (Interpreter.length(args) == 2)
                {
                    Cons c1 = (Cons) args;
                    Cons c2 = (Cons) c1.cdr;
                    if (c1.car instanceof StringAtom)
                    {
                        String modelClipName = ((StringAtom) c1.car).value;
                        if (c2.car instanceof FloatAtom)
                        {
                            float time = ((FloatAtom) c2.car).value;
                            int at = modelClipName.lastIndexOf('@');
                            String location = modelClipName.substring(0, at);
                            String clipName = modelClipName.substring(at + 1, modelClipName.length());
                            ResourceLocation model;
                            if (location.indexOf('#') != -1)
                            {
                                model = new ModelResourceLocation(location);
                            }
                            else
                            {
                                model = new ResourceLocation(location);
                            }
                            // TODO: cache?
                            return new ClipValue(Clips.getModelClipNode(model, clipName), time);
                        }
                    }
                }
                throw new IllegalArgumentException("&model_clip needs a string and a float argument, got " + args);
            }
        },
        TriggerPositive("trigger_positive")
        {
            @Override
            public ISExp apply(IList args)
            {
                if (Interpreter.length(args) == 3)
                {
                    Cons c1 = (Cons) args;
                    Cons c2 = (Cons) c1.cdr;
                    Cons c3 = (Cons) c2.cdr;
                    if (c1.car instanceof ClipValue)
                    {
                        ClipValue clip = (ClipValue) c1.car;
                        if (c2.car instanceof FloatAtom)
                        {
                            float trigger = ((FloatAtom) c2.car).value;
                            if(c3.car instanceof StringAtom)
                            {
                                String event = ((StringAtom) c3.car).value;
                                // FIXME
                                return new ClipValue(new Clips.TriggerClip(clip.clip, new TimeValues.ConstValue(-1), event), clip.time);
                            }
                        }
                    }
                }
                throw new IllegalArgumentException("&trigger_positive needs a clip, a float and a string arguments, got " + args);
            }
        };

        private static final ImmutableMap<ISExp, GlueOp> values;

        static
        {
            ImmutableMap.Builder<ISExp, GlueOp> builder = ImmutableMap.builder();
            for (GlueOp op : values())
            {
                builder.put(op.symbol, op);
            }
            values = builder.build();
        }

        private final ISExp symbol;
        private final String name;

        GlueOp(String name)
        {
            this.name = name;
            this.symbol = makeSymbol(name);
        }

        @Override
        public String toString()
        {
            return "&" + name;
        }
    }*/

    public static IAnimationStateMachine loadASM(ResourceLocation location, ImmutableMap<String, ITimeValue> customParameters) throws IOException
    {
        ISExp source = read(location);
        return PlonAnimationStateMachine.create(source, customParameters);
    }

    public static ISExp read(ResourceLocation location) throws IOException
    {
        ResourceLocation fileLocation = getPlonLocation(location);
        Reader reader;
        if(manager == null && (Launch.blackboard == null || (Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment")))
        {
            if(location.equals(rootLibraryLocation))
            {
                reader = new InputStreamReader(Glue.class.getResourceAsStream("/assets/" + location.getResourceDomain() + "/plon/" + location.getResourcePath() + ".json"));
            }
            else
            {
                reader = new FileReader(location.getResourcePath() + ".json");
            }
        }
        else
        {
            IResource resource = manager.getResource(fileLocation);
            reader = new InputStreamReader(resource.getInputStream(), Charsets.UTF_8);
        }
        // TODO cache
        return sexpGson.fromJson(reader, ISExp.class);
    }

    private static final Gson sexpGson = new GsonBuilder().registerTypeAdapterFactory(AST.SExpTypeAdapterFactory.INSTANCE).create();

    private static final ISExp statesKey = AST.makeString("states");
    private static final ISExp transitionsKey = AST.makeString("transitions");
    private static final ISExp startStateKey = AST.makeString("start_state");

    private static final class PlonAnimationStateMachine extends AnimationStateMachineBase
    {
        private final ImmutableMap<ISExp, ISExp> rootFrame;
        private final ISExp asmSource;

        private ImmutableMap<String, Float> lastTriggers = ImmutableMap.of();

        private PlonAnimationStateMachine(ImmutableMap<ISExp, ISExp> rootFrame, ISExp asmSource, final ImmutableMap<String, ITimeValue> customParameters, ImmutableList<String> states, ImmutableMultimap<String, String> transitions, String startState)
        {
            super(customParameters, states, transitions, startState);
            this.asmSource = asmSource;
            this.rootFrame = rootFrame;
            initialize();
        }

        private static ImmutableMap<ISExp, ISExp> makeRootFrame(final ImmutableMap<String, ITimeValue> customParameters)
        {
            if(rootLibrary == null)
            {
                loadRootLibrary();
            }
            ISExp userResolver = getUserOp(new Function<String, Optional<? extends ITimeValue>>()
            {
                @Override
                public Optional<? extends ITimeValue> apply(String name)
                {
                    if (customParameters.containsKey(name))
                    {
                        return Optional.of(customParameters.get(name));
                    }
                    return Optional.absent();
                }
            });
            java.util.Map<ISExp, ISExp> rootFrame = Maps.newHashMap();
            //rootFrame.putAll(GlueOp.values);
            rootFrame.put(AST.makeSymbol("user"), userResolver);
            rootFrame.putAll(rootLibrary.value);
            return ImmutableMap.copyOf(rootFrame);
        }

        private static PlonAnimationStateMachine create(ISExp asmSource, final ImmutableMap<String, ITimeValue> customParameters)
        {
            ImmutableMap<ISExp, ISExp> rootFrame = makeRootFrame(customParameters);
            ISExp source = new Cons(makeSymbol("asm_def"), new Cons(asmSource, Nil.INSTANCE));
            ImmutableMap.Builder<ISExp, ISExp> frameBuilder = ImmutableMap.builder();
            frameBuilder.putAll(rootFrame);
            frameBuilder.put(makeSymbol("time"), makeFloat(0));
            ImmutableMap<ISExp, ISExp> checkFrame = frameBuilder.build();
            // type check that result of asm_def is a map
            Unifier unifier = new Unifier();
            ISExp sType = in.infer(source, checkFrame, unifier);
            unifier.unify(sType, PrimTypes.Map.type);
            // and run it
            ISExp asmDef = in.eval(source, checkFrame);
            unifier = new Unifier();
            // type check that result of asm_run is a map
            ISExp runType = in.infer(new Cons(makeSymbol("asm_run"), new Cons(asmSource, new Cons(makeString(null), Nil.INSTANCE))), checkFrame, unifier);
            unifier.unify(runType, PrimTypes.Map.type);
            // FIXME make exception strings less silly
            Map asm = (Map) asmDef;
            if(!asm.value.containsKey(statesKey))
            {
                throw new IllegalArgumentException("asm_def applied to asm loaded from plon needs to eval to a map with a \"states\" key");
            }
            if(!asm.value.containsKey(transitionsKey))
            {
                throw new IllegalArgumentException("asm_def applied to asm loaded from plon needs to eval to a map with a \"transitions\" key");
            }
            if(!asm.value.containsKey(startStateKey))
            {
                throw new IllegalArgumentException("asm_def applied to asm loaded from plon needs to eval to a map with a \"start_state\" key");
            }
            ISExp exp = asm.value.get(statesKey);
            if(!(exp instanceof Cons))
            {
                throw new IllegalArgumentException("asm_def applied to asm loaded from plon needs to eval to a map with a \"states\" key with a list value, got: " + exp);
            }
            IList list = (IList) exp;
            ImmutableList.Builder<String> stateBuilder = ImmutableList.builder();
            while(list != Nil.INSTANCE)
            {
                Cons cons = (Cons) list;
                ISExp state = cons.car;
                if(!(state instanceof StringAtom))
                {
                    throw new IllegalArgumentException("asm_def applied to asm loaded from plon needs to eval to a map with a \"states\" key with a list value, got: " + state);
                }
                stateBuilder.add(((StringAtom) state).value);
                list = cons.cdr;
            }
            exp = asm.value.get(transitionsKey);
            if(!(exp instanceof IMap))
            {
                throw new IllegalArgumentException("asm_def applied to asm loaded from plon needs to eval to a map with a \"transitions\" key with a map value, got: " + exp);
            }
            ImmutableMultimap<String, String> transitions;
            if(exp == MNil.INSTANCE)
            {
                transitions = ImmutableMultimap.of();
            }
            else
            {
                Map map = (Map) exp;
                ImmutableMultimap.Builder<String, String> builder = ImmutableMultimap.builder();
                for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
                {
                    if(!(entry.getKey() instanceof StringAtom))
                    {
                        throw new IllegalArgumentException("transition multimap can only contain string keys, got: " + entry.getKey());
                    }
                    String key = ((StringAtom) entry.getKey()).value;
                    if(entry.getValue() instanceof StringAtom)
                    {
                        builder.put(key, ((StringAtom) entry.getValue()).value);
                    }
                    else if(entry.getValue() instanceof IList)
                    {
                        IList tos = (IList) entry.getValue();
                        while(tos != Nil.INSTANCE)
                        {
                            ISExp to = ((Cons) tos).car;
                            if(!(to instanceof StringAtom))
                            {
                                throw new IllegalArgumentException("transition multimap can only contain string values, got: " + to);
                            }
                            builder.put(key, ((StringAtom) to).value);
                            tos = ((Cons) tos).cdr;
                        }
                    }
                    else
                    {
                        throw new IllegalArgumentException("transition multimap can only contain string values, got: " + entry.getValue());
                    }
                }
                transitions = builder.build();
            }

            exp = asm.value.get(startStateKey);
            if(!(exp instanceof StringAtom))
            {
                throw new IllegalArgumentException("asm_def applied to asm loaded from plon needs to eval to a map with a \"start_state\" key with a string value, got: " + exp);
            }
            return new PlonAnimationStateMachine(rootFrame, asmSource, customParameters, stateBuilder.build(), transitions, ((StringAtom) exp).value);
        }

        private ISExp applyAsm(String state, float time)
        {
            ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
            builder.putAll(rootFrame);
            builder.put(makeSymbol("time"), makeFloat(time));
            return in.eval(new Cons(makeSymbol("asm_run"), new Cons(asmSource, new Cons(makeString(state), Nil.INSTANCE))), builder.build());
        }

        @Override
        public Pair<IModelState, Iterable<Event>> apply(float time)
        {
            /*ISExp exp = applyAsm(currentState(), time);
            if(!(exp instanceof ClipValue))
            {
                throw new IllegalArgumentException("asm_run applied to asm loaded from plon needs to eval to a clip, got: " + exp);
            }
            ClipValue clip = (ClipValue) exp;*/
            ISExp clip = applyAsm(currentState(), time);
            if(clip instanceof Cons && length(clip) == 3)
            {
                Cons c1 = (Cons) clip;
                Cons c2 = (Cons) c1.cdr;
                Cons c3 = (Cons) c2.cdr;
                if(c1.car instanceof StringAtom && c2.car instanceof FloatAtom && c3.car instanceof IMap)
                {
                    String modelClipName = ((StringAtom) c1.car).value;
                    int at = modelClipName.lastIndexOf('@');
                    String location = modelClipName.substring(0, at);
                    String clipName = modelClipName.substring(at + 1, modelClipName.length());
                    ResourceLocation model;
                    if (location.indexOf('#') != -1)
                    {
                        model = new ModelResourceLocation(location);
                    }
                    else
                    {
                        model = new ResourceLocation(location);
                    }
                    IClip rootClip = Clips.getModelClipNode(model, clipName);
                    float clipTime = ((FloatAtom) c2.car).value;
                    if(lastPollTime == Float.NEGATIVE_INFINITY)
                    {
                        lastPollTime = clipTime;
                    }
                    Pair<IModelState, Iterable<Event>> pair = Clips.apply(rootClip, lastPollTime, clipTime);
                    ImmutableMap<String, Float> triggers;
                    if(c3.car == MNil.INSTANCE)
                    {
                        triggers = ImmutableMap.of();
                    }
                    else
                    {
                        Map map = (Map) c3.car;
                        ImmutableMap.Builder<String, Float> builder = ImmutableMap.builder();
                        ImmutableList.Builder<Event> eventBuilder = ImmutableList.builder();
                        for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
                        {
                            if(!(entry.getKey() instanceof StringAtom) || !(entry.getValue() instanceof Cons) || length(entry.getValue()) != 2)
                            {
                                throw new IllegalStateException("asm_run's returned map should only contain string keys and pair values, got: " + entry);
                            }
                            String triggerName = ((StringAtom) entry.getKey()).value;
                            Cons c4 = (Cons) entry.getValue();
                            Cons c5 = (Cons) c4.cdr;
                            if(!(c4.car instanceof FloatAtom) || !(c5.car instanceof StringAtom))
                            {
                                throw new IllegalStateException("asm_run's returned map values should be pairs of floats and strings, got: " + entry.getValue());
                            }
                            float value = ((FloatAtom) c4.car).value;
                            String event = ((StringAtom) c5.car).value;
                            builder.put(triggerName, value);
                            if((!lastTriggers.containsKey(triggerName) || lastTriggers.get(triggerName) < 0) && value >= 0)
                            {
                                eventBuilder.add(new Event(event, 0));
                            }
                        }
                        triggers = builder.build();
                        ImmutableList<Event> events = eventBuilder.build();
                        if(!events.isEmpty())
                        {
                            pair = Pair.of(pair.getLeft(), Iterables.concat(pair.getRight(), events));
                        }
                    }
                    lastPollTime = clipTime;
                    lastTriggers = triggers;
                    return filterSpecialEvents(pair);
                }
            }
            throw new IllegalStateException("asm_run should return a list of the name, time and a map from trigger parameter names to their values, got" + clip);
        }

        @Override
        public void transition(String newState)
        {
            super.transition(newState);
            lastTriggers = ImmutableMap.of();
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager)
    {
        Glue.manager = manager;
    }

    private static void loadRootLibrary()
    {
        ISExp exp;
        try
        {
            exp = read(rootLibraryLocation);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Exception reading root plon library", e);
        }
        if(!(exp instanceof Map))
        {
            throw new IllegalStateException("Root plon library isn't a map");
        }
        Map map = (Map) exp;
        map = AST.loadFrame(map);
        ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
        //builder.putAll(GlueOp.values);
        builder.putAll(map.value);
        rootLibrary = new Map(builder.build());
    }
}
